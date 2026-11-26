package com.otusfoss.mail.feature.read.data

import com.otusfoss.mail.feature.read.domain.UnsubscribeResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OneClickPostTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val url = "https://lists.example.org/u/1?token=abc"

    private fun post(
        status: HttpStatusCode = HttpStatusCode.OK,
        location: String? = null,
    ) = OneClickPost(
        MockEngine { request ->
            requests += request
            respond("", status, location?.let { headersOf(HttpHeaders.Location, it) } ?: headersOf())
        },
    )

    @Test
    fun theListGetsTheFixedPairAsAForm() =
        runTest {
            assertEquals(UnsubscribeResult.Done, post().send(url))
            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals(url, request.url.toString())
            val body = request.body as TextContent
            assertEquals("List-Unsubscribe=One-Click", body.text)
            assertEquals("application/x-www-form-urlencoded", body.contentType.withoutParameters().toString())
        }

    @Test
    fun aListThatRefusesLeavesItsPageToTheUser() =
        runTest {
            assertEquals(UnsubscribeResult.OpenLink(url), post(HttpStatusCode.NotFound).send(url))
            val moved = post(HttpStatusCode.Found, location = "https://elsewhere.example/u")
            assertEquals(UnsubscribeResult.OpenLink(url), moved.send(url), "A redirect is an answer, not a success")
        }

    @Test
    fun aListThatCannotBeReachedFailsSoTheUserCanTryAgain() =
        runTest {
            val offline = OneClickPost(MockEngine { throw IOException("no route to host") })
            assertEquals(UnsubscribeResult.Failed, offline.send(url))
        }

    @Test
    fun anAddressTheEngineRefusesOpensAsAPage() =
        runTest {
            val refusing = OneClickPost(MockEngine { throw IllegalArgumentException("Invalid URL host") })
            assertEquals(UnsubscribeResult.OpenLink(url), refusing.send(url))
        }

    @Test
    fun onlyAPublicHttpsAddressGetsARequest() =
        runTest {
            val post = post()
            listOf(
                "http://lists.example.org/u/1",
                "https://ada:secret@lists.example.org/u/1",
                "https://lists.example.org:8443/u/1",
                "https://nas.lan/u/1",
                "https://192.168.1.2/u/1",
                "https://lists.example.org%/u/1",
                "not a url",
            ).forEach { assertEquals(UnsubscribeResult.OpenLink(it), post.send(it), it) }
            assertTrue(requests.isEmpty(), "No request leaves the device: ${requests.map { it.url }}")
        }
}
