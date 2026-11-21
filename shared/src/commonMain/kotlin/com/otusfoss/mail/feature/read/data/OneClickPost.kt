package com.otusfoss.mail.feature.read.data

import com.otusfoss.core.avatar.isPublicHttpsUrl
import com.otusfoss.mail.feature.read.domain.UnsubscribeResult
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.io.IOException

/**
 * The one-click unsubscribe request of RFC 8058: an HTTPS POST of [ONE_CLICK] to the address in the
 * list's `List-Unsubscribe` header. The sender wrote that address, and a forged message can name
 * any, so only an address that passes [isPublicHttpsUrl] gets a request: a device in the user's
 * network with a trusted certificate on another port gets none. The request carries no cookies or
 * credentials (section 3.1) and never reads the answer's body. The user's tap on Unsubscribe is the
 * consent section 3.2 asks for.
 */
internal class OneClickPost(
    engine: HttpClientEngine? = null,
) {
    private val http by lazy {
        if (engine == null) HttpClient { configure() } else HttpClient(engine) { configure() }
    }

    /**
     * [UnsubscribeResult.Done] when the list answers [url] with a 2xx status. A list that answers
     * anything else, or whose address gets no request, leaves its page to the user. One that cannot
     * be reached fails, so the user can try again.
     */
    suspend fun send(url: String): UnsubscribeResult {
        if (!isPublicHttpsUrl(url)) return UnsubscribeResult.OpenLink(url)
        return try {
            http
                .preparePost(url) { setBody(TextContent(ONE_CLICK, ContentType.Application.FormUrlEncoded)) }
                .execute { if (it.status.isSuccess()) UnsubscribeResult.Done else UnsubscribeResult.OpenLink(url) }
        } catch (expected: IOException) {
            UnsubscribeResult.Failed
        } catch (expected: IllegalArgumentException) {
            // The HTTP engine refused to build a request for this address, so a retry cannot help.
            UnsubscribeResult.OpenLink(url)
        }
    }

    private fun HttpClientConfig<*>.configure() {
        // Ktor follows no redirect of a POST today. This setting keeps it so.
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = TIMEOUT_MILLIS
            requestTimeoutMillis = TIMEOUT_MILLIS
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}

/**
 * The only value of `List-Unsubscribe-Post` and the form body of the request (RFC 8058, section 5).
 * Section 3.2 allows `application/x-www-form-urlencoded`, and this pair needs no escaping in it.
 */
internal const val ONE_CLICK = "List-Unsubscribe=One-Click"
