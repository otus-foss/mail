package com.otus.mail.core.avatar

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpAvatarImageLoaderTest {
    private val pngHeaders = headersOf(HttpHeaders.ContentType, "image/png")
    private val htmlHeaders = headersOf(HttpHeaders.ContentType, "text/html")
    private val svgHeaders = headersOf(HttpHeaders.ContentType, "image/svg+xml")

    @Test
    fun publicImagesNeverReceiveCredentials() =
        runTest {
            val bytes = byteArrayOf(1, 2, 3)
            val loader =
                HttpAvatarImageLoader(
                    avatarEngine { request ->
                        assertNull(request.headers[HttpHeaders.Authorization])
                        assertNull(request.headers[HttpHeaders.Cookie])
                        respond(bytes, headers = pngHeaders)
                    },
                )
            try {
                assertContentEquals(bytes, loader.load("https://company.example/favicon.png")?.bytes)
            } finally {
                loader.close()
            }
        }

    @Test
    fun insecureCredentialBearingAndInternalUrlsAreRefusedBeforeFetching() =
        runTest {
            var calls = 0
            val loader =
                HttpAvatarImageLoader(
                    avatarEngine {
                        calls++
                        error("URL must be refused")
                    },
                )
            try {
                listOf(
                    "http://company.example/a.png",
                    "https://user:pass@company.example/a.png",
                    "file:///a.png",
                    "https://localhost/a.png",
                    "https://127.0.0.1/a.png",
                ).forEach { assertNull(loader.load(it)) }
                assertEquals(0, calls)
            } finally {
                loader.close()
            }
        }

    @Test
    fun publicHttpsRedirectsResolveRelativeAndCdnIconsWithoutCredentials() =
        runTest {
            val urls = mutableListOf<String>()
            val bytes = byteArrayOf(1, 2, 3)
            val loader =
                HttpAvatarImageLoader(
                    avatarEngine { request ->
                        urls += request.url.toString()
                        assertNull(request.headers[HttpHeaders.Authorization])
                        assertNull(request.headers[HttpHeaders.Cookie])
                        when (request.url.encodedPath) {
                            "/favicon.png" -> {
                                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/icons/icon"))
                            }

                            "/icons/icon" -> {
                                respond(
                                    "",
                                    HttpStatusCode.MovedPermanently,
                                    headersOf(HttpHeaders.Location, "https://cdn.example/icon.png"),
                                )
                            }

                            else -> {
                                respond(bytes, headers = pngHeaders)
                            }
                        }
                    },
                )
            try {
                assertContentEquals(bytes, loader.load("https://company.example/favicon.png?old=1")?.bytes)
                assertEquals(
                    listOf(
                        "https://company.example/favicon.png?old=1",
                        "https://company.example/icons/icon",
                        "https://cdn.example/icon.png",
                    ),
                    urls,
                )
            } finally {
                loader.close()
            }
        }

    @Test
    fun unsafeRedirectTargetsAreRefusedBeforeFetching() =
        runTest {
            for (target in listOf(
                "http://company.example/icon.png",
                "https://user:pass@company.example/icon.png",
                "https://localhost/icon.png",
                "https://127.0.0.1/icon.png",
                "file:///icon.png",
            )) {
                var calls = 0
                val loader =
                    HttpAvatarImageLoader(
                        avatarEngine {
                            calls++
                            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, target))
                        },
                    )
                try {
                    assertNull(loader.load("https://company.example/favicon.png"))
                    assertEquals(1, calls)
                } finally {
                    loader.close()
                }
            }
        }

    @Test
    fun redirectLoopsAndLongChainsAreBounded() =
        runTest {
            for (loop in listOf(true, false)) {
                var calls = 0
                val loader =
                    HttpAvatarImageLoader(
                        avatarEngine {
                            calls++
                            val next =
                                if (loop) "https://company.example/icon" else "https://company.example/icon$calls"
                            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, next))
                        },
                    )
                try {
                    assertNull(loader.load("https://company.example/icon"))
                    assertEquals(if (loop) 1 else 4, calls)
                } finally {
                    loader.close()
                }
            }
        }

    @Test
    fun oversizedBodiesAreRejectedWithoutAContentLength() =
        runTest {
            val loader =
                HttpAvatarImageLoader(
                    avatarEngine {
                        val bytes = ByteArray(MAX_AVATAR_BYTES.toInt() + 1)
                        respond(bytes, headers = pngHeaders)
                    },
                )
            try {
                assertNull(loader.load("https://company.example/a.png"))
            } finally {
                loader.close()
            }
        }

    @Test
    fun htmlSvgAndHttpErrorsDoNotBecomeAvatars() =
        runTest {
            val loader =
                HttpAvatarImageLoader(
                    avatarEngine { request ->
                        when (request.url.encodedPath) {
                            "/html" -> respond("<html/>", headers = htmlHeaders)
                            "/svg" -> respond("<svg/>", headers = svgHeaders)
                            else -> respond("", HttpStatusCode.NotFound)
                        }
                    },
                )
            try {
                listOf("html", "svg", "missing").forEach { assertNull(loader.load("https://company.example/$it")) }
            } finally {
                loader.close()
            }
        }

    @Test
    fun timeoutIncludesTheEntireRedirectChain() =
        runTest {
            var calls = 0
            val loader =
                HttpAvatarImageLoader(
                    avatarEngine {
                        calls++
                        delay(3_000)
                        respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/icon$calls"))
                    },
                )
            try {
                assertNull(loader.load("https://company.example/favicon.png"))
                assertEquals(2, calls)
            } finally {
                loader.close()
            }
        }

    private fun TestScope.avatarEngine(handler: MockRequestHandler): MockEngine =
        MockEngine(
            MockEngineConfig().apply {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler(handler)
            },
        )
}
