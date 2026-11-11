package com.otusfoss.mail.feature.updates.data

import com.otusfoss.mail.feature.updates.domain.NightlyBuild
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GitHubNightlyFeedTest {
    private fun feed(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = GitHubNightlyFeed(HttpClient(MockEngine { respond(body, status) }), "owner/nightly")

    @Test
    fun releasesGiveTheirNumbersApksAndNotesNewestFirst() =
        runTest {
            val body =
                """
                [
                  {"tag_name": "nightly-141", "body": "- fix(inbox): b", "published_at": "2026-10-13T22:47:55Z", "assets": [
                    {"name": "otus-mail-nightly-141.apk", "browser_download_url": "$DOWNLOADS/nightly-141/a.apk", "digest": "sha256:$HASH"}
                  ]},
                  {"tag_name": "nightly-142", "body": "- feat(sidebar): a\n- ci: c", "assets": [
                    {"name": "notes.txt", "browser_download_url": "$DOWNLOADS/nightly-142/notes.txt"},
                    {"name": "otus-mail-nightly-142.apk", "browser_download_url": "$DOWNLOADS/nightly-142/b.apk", "digest": "sha256:$HASH"}
                  ]}
                ]
                """.trimIndent()
            assertEquals(
                listOf(
                    NightlyBuild(
                        142,
                        "$DOWNLOADS/nightly-142/b.apk",
                        listOf("- feat(sidebar): a", "- ci: c"),
                        "",
                        HASH,
                    ),
                    NightlyBuild(141, "$DOWNLOADS/nightly-141/a.apk", listOf("- fix(inbox): b"), "2026-10-13", HASH),
                ),
                feed(body).releases(),
            )
        }

    @Test
    fun noRepositoryOrAnUnknownTagGivesNothing() =
        runTest {
            assertTrue(feed("{}", HttpStatusCode.NotFound).releases().isEmpty())
            assertTrue(feed("""[{"tag_name": "v1.0", "assets": []}]""").releases().isEmpty())
        }

    @Test
    fun anApkFromElsewhereOrWithoutAHashIsNoBuild() =
        runTest {
            listOf(
                "http://github.com/owner/nightly/releases/download/nightly-1/a.apk" to HASH,
                "https://evil.example/owner/nightly/releases/download/nightly-1/a.apk" to HASH,
                "https://github.com.evil.example/owner/nightly/releases/download/nightly-1/a.apk" to HASH,
                "https://github.com/other/nightly/releases/download/nightly-1/a.apk" to HASH,
                "https://github.com/owner/nightly/releases/download/../../../other/a.apk" to HASH,
                "https://github.com:8443/owner/nightly/releases/download/nightly-1/a.apk" to HASH,
                "$DOWNLOADS/nightly-1/a.apk" to "",
                "$DOWNLOADS/nightly-1/a.apk" to "md5:abc",
            ).forEach { (url, digest) ->
                val body =
                    """[{"tag_name": "nightly-1", "assets": [
                      {"name": "a.apk", "browser_download_url": "$url", "digest": "$digest"}]}]"""
                assertTrue(feed(body).releases().isEmpty(), url)
            }
        }

    @Test
    fun serverErrorFailsTheCheck() =
        runTest {
            assertFailsWith<IllegalStateException> { feed("", HttpStatusCode.InternalServerError).releases() }
        }

    private companion object {
        const val DOWNLOADS = "https://github.com/owner/nightly/releases/download"
        const val HASH = "853466542bab0cab90b950a43e1c43949e420f83d6ba8a0952307f77295dbac8"
    }
}
