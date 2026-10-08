package com.otus.jmap

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JmapDiscoveryTest {
    private val wellKnown = "https://mail.example.org/.well-known/jmap"

    @Test
    fun followsARelativeRedirectAndResolvesTheRelativeApiUrl() =
        runTest {
            val (engine, requests) = server()
            val session = JmapClient(engine).session(wellKnown, ADA)
            assertEquals("https://mail.example.org/jmap/session", session.sessionUrl)
            assertEquals("https://mail.example.org/jmap/", session.apiUrl)
            assertEquals("d1", session.accountFor(JmapCapability.MAIL))
            assertEquals("s1", session.state)
            assertTrue(requests.all { it.authorized }, "The origin the user entered receives credentials")
        }

    @Test
    fun capabilityObjectsAndTheUploadTemplateAreKept() =
        runTest {
            val engine =
                MockEngine {
                    json(
                        """{"capabilities": {"urn:ietf:params:jmap:core": {"maxObjectsInSet": 500,
                           "maxSizeUpload": "huge"}, "urn:example:odd": true},
                           "accounts": {"d1": {"name": "Ada", "accountCapabilities": {
                             "urn:ietf:params:jmap:sieve": {"sieveExtensions": ["fileinto", "mailboxid"]}}}},
                           "apiUrl": "/jmap/", "uploadUrl": "/upload/{accountId}/"}""",
                    )
                }
            val session = JmapClient(engine).session(wellKnown, ADA)
            assertEquals("/upload/{accountId}/", session.uploadUrl)
            assertEquals(500, session.maxObjectsInSet)
            assertEquals(null, session.maxSizeUpload, "A malformed limit reads as unknown")
            assertEquals(emptyMap(), session.capabilityObjects.getValue("urn:example:odd"))
            val sieve =
                session.accounts
                    .getValue("d1")
                    .capabilityObjects
                    .getValue(JmapCapability.SIEVE)
            assertTrue("mailboxid" in sieve.getValue("sieveExtensions").toString())
        }

    @Test
    fun credentialsNeverReachAnotherHostWithoutConsent() =
        runTest {
            val requests = mutableListOf<Recorded>()
            val engine =
                MockEngine { request ->
                    requests += request.record()
                    if (request.url.host ==
                        "mail.example.org"
                    ) {
                        redirect("https://login.example.net/session")
                    } else {
                        json(session())
                    }
                }
            val error =
                assertFailsWith<JmapException.RedirectedElsewhere> { JmapClient(engine).session(wellKnown, ADA) }
            assertEquals("login.example.net", error.host)
            assertTrue(
                requests.none { it.host == "login.example.net" && it.authorized },
                "No credentials to an untrusted host",
            )

            requests.clear()
            val session = JmapClient(engine).session(wellKnown, ADA, setOf(error.origin))
            assertEquals("https://login.example.net/jmap/", session.apiUrl)
            assertTrue(requests.last().authorized, "An approved origin receives credentials")
        }

    @Test
    fun anApiUrlOnAnotherHostNeedsConsentToo() =
        runTest {
            val engine = MockEngine { json(session(apiUrl = "https://api.example.net/jmap/")) }
            val error =
                assertFailsWith<JmapException.RedirectedElsewhere> { JmapClient(engine).session(wellKnown, ADA) }
            assertEquals("api.example.net", error.host)
        }

    @Test
    fun plainHttpIsRefusedEverywhere() =
        runTest {
            val any = MockEngine { json(session()) }
            assertFailsWith<JmapException.InsecureUrl> {
                JmapClient(
                    any,
                ).session("http://mail.example.org/jmap/session", ADA)
            }
            val downgrade = MockEngine { redirect("http://mail.example.org/jmap/session") }
            assertFailsWith<JmapException.InsecureUrl> { JmapClient(downgrade).session(wellKnown, ADA) }
            val insecureApi = MockEngine { json(session(apiUrl = "http://mail.example.org/jmap/")) }
            assertFailsWith<JmapException.InsecureUrl> { JmapClient(insecureApi).session(wellKnown, ADA) }
        }

    @Test
    fun httpIsAllowedOnlyByAnExplicitTestPolicy() =
        runTest {
            val engine = MockEngine { json(session()) }
            val session =
                JmapClient(
                    engine,
                    JmapPolicy(allowInsecureHttp = true),
                ).session("http://localhost:8080/jmap/session", ADA)
            assertEquals("http://localhost:8080/jmap/", session.apiUrl)
        }

    @Test
    fun statusCodesMapToDistinctFailures() =
        runTest {
            suspend fun failure(
                status: HttpStatusCode,
                body: String = "",
            ) = runCatching {
                JmapClient(
                    MockEngine { respond(body, status) },
                ).session(wellKnown, ADA)
            }.exceptionOrNull()
            assertTrue(failure(HttpStatusCode.Unauthorized) is JmapException.Unauthorized)
            assertTrue(failure(HttpStatusCode.Forbidden) is JmapException.Forbidden)
            assertTrue(failure(HttpStatusCode.NotFound) is JmapException.NotJmap)
            assertTrue(failure(HttpStatusCode.OK, "<html></html>") is JmapException.NotJmap)
            val problem = failure(HttpStatusCode.BadRequest, """{"type": "urn:ietf:params:jmap:error:limit"}""")
            assertEquals("urn:ietf:params:jmap:error:limit", (problem as JmapException.RequestFailed).type)
        }

    @Test
    fun redirectLoopsAndBadUrlsFailCleanly() =
        runTest {
            val loop = MockEngine { redirect("/.well-known/jmap") }
            assertFailsWith<JmapException.TooManyRedirects> { JmapClient(loop).session(wellKnown, ADA) }
            assertFailsWith<JmapException.InsecureUrl> { JmapClient(loop).session("not a url", ADA) }
        }
}
