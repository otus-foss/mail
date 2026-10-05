package com.otus.mail.core.avatar

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentLength
import io.ktor.http.takeFrom
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray

/** Credential-free client. Checks every redirect, and bounds time, hops, and raster response size. */
class HttpAvatarImageLoader(
    engine: HttpClientEngine? = null,
) : AvatarImageLoader {
    private val http = if (engine == null) HttpClient { configure() } else HttpClient(engine) { configure() }

    override suspend fun websiteIcon(domain: String): AvatarImage? = HttpWebsiteIconSource(this).icon(domain)

    override suspend fun load(url: String): AvatarImage? =
        avatarOrNull {
            withTimeoutOrNull(AVATAR_TIMEOUT_MILLIS) { loadRedirects(Url(url), page = false)?.image }
        }

    /** Read only the bounded beginning of HTML, and retain the final URL for relative icon links. */
    internal suspend fun websitePage(url: String): AvatarWebsitePage? =
        runCatching {
            withTimeoutOrNull(AVATAR_TIMEOUT_MILLIS) { loadRedirects(Url(url), page = true)?.page }
        }.getOrElse {
            if (it is CancellationException) throw it
            null
        }

    private suspend fun loadRedirects(
        initial: Url,
        page: Boolean,
    ): ImageResponse? {
        var target = initial
        val visited = mutableSetOf<String>()
        var requests = 0
        while (requests <= MAX_REDIRECTS) {
            if (!target.isPublicImageUrl() || !visited.add(target.toString())) break
            requests++
            val result = http.prepareGet(target).execute { response -> readResponse(response, target, page) }
            val location = result.location ?: return result
            target =
                URLBuilder()
                    .takeFrom(target)
                    .apply {
                        parameters.clear()
                        fragment = ""
                        takeFrom(location)
                    }.build()
        }
        return null
    }

    private suspend fun readResponse(
        response: HttpResponse,
        target: Url,
        page: Boolean,
    ): ImageResponse {
        val type =
            response.headers[HttpHeaders.ContentType]
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
        return when {
            response.status.value in REDIRECTS -> {
                ImageResponse(location = response.headers[HttpHeaders.Location])
            }

            response.status.value !in SUCCESS -> {
                ImageResponse()
            }

            page && type == "text/html" -> {
                val html =
                    response
                        .bodyAsChannel()
                        .readBuffer(MAX_HTML_BYTES)
                        .readByteArray()
                        .decodeToString()
                ImageResponse(page = AvatarWebsitePage(target.toString(), html))
            }

            !page && type in RASTER_TYPES && (response.contentLength() ?: 0) <= MAX_AVATAR_BYTES -> {
                val bytes = response.bodyAsChannel().readBuffer(MAX_AVATAR_BYTES + 1).readByteArray()
                ImageResponse(bytes.takeIf { it.isNotEmpty() && it.size <= MAX_AVATAR_BYTES }?.let(::AvatarImage))
            }

            else -> {
                ImageResponse()
            }
        }
    }

    private fun Url.isPublicImageUrl(): Boolean =
        protocol == URLProtocol.HTTPS && user.isNullOrEmpty() && password.isNullOrEmpty() &&
            isPublicAvatarHost(host)

    private class ImageResponse(
        val image: AvatarImage? = null,
        val location: String? = null,
        val page: AvatarWebsitePage? = null,
    )

    fun close() = http.close()

    private fun HttpClientConfig<*>.configure() {
        followRedirects = false
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = AVATAR_TIMEOUT_MILLIS
            requestTimeoutMillis = AVATAR_TIMEOUT_MILLIS
        }
    }

    private companion object {
        val SUCCESS = 200..299
        val RASTER_TYPES = setOf("image/png", "image/jpeg", "image/webp", "image/x-icon", "image/vnd.microsoft.icon")
        const val AVATAR_TIMEOUT_MILLIS = 5_000L
        const val MAX_REDIRECTS = 3
        const val MAX_HTML_BYTES = 64 * 1024L
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}

internal class AvatarWebsitePage(
    val url: String,
    val html: String,
)

/** Limits each image and the service's worst-case encoded cache to 32 MiB at default capacity. */
const val MAX_AVATAR_BYTES = 512L * 1024
