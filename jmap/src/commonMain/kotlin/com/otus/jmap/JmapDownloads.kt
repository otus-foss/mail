package com.otus.jmap

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentLength
import io.ktor.http.encodeURLParameter
import io.ktor.utils.io.readBuffer
import kotlinx.io.IOException
import kotlinx.io.readByteArray

public class JmapBlob(
    public val bytes: ByteArray,
    public val mediaType: String,
)

internal class JmapDownloads(
    private val http: HttpClient,
    private val urls: JmapUrls,
    private val policy: JmapPolicy,
) {
    suspend fun download(
        session: JmapSession,
        auth: JmapAuth,
        accountId: String,
        blobId: String,
        mediaType: String,
        maxBytes: Long,
    ): JmapBlob {
        require(maxBytes > 0 && maxBytes <= policy.maxResponseBytes)
        require(accountId in session.accounts)
        val target = downloadTarget(session, accountId, blobId, mediaType)
        return try {
            http
                .prepareGet(target) {
                    header(HttpHeaders.Authorization, auth.header())
                    header(HttpHeaders.Accept, mediaType)
                }.execute { response ->
                    RawResponse(response.status.value, null, "").failure()?.let { throw it }
                    checkBlobSize(response.contentLength() ?: 0, maxBytes)
                    val bytes = response.bodyAsChannel().readBuffer(maxBytes + 1).readByteArray()
                    checkBlobSize(bytes.size.toLong(), maxBytes)
                    JmapBlob(bytes, response.headers[HttpHeaders.ContentType]?.substringBefore(';') ?: mediaType)
                }
        } catch (error: IOException) {
            throw JmapException.Unreachable(error)
        }
    }

    private fun downloadTarget(
        session: JmapSession,
        accountId: String,
        blobId: String,
        mediaType: String,
    ): Url {
        val template = session.downloadUrl ?: throw JmapException.NotJmap("no blob download endpoint")
        val expanded = expandDownload(template, accountId, blobId, mediaType)
        val target = urls.resolve(urls.parse(session.sessionUrl), expanded)
        if (target.origin() !in session.trustedOrigins) {
            throw JmapException.RedirectedElsewhere(target.host, target.origin())
        }
        return target
    }
}

private fun checkBlobSize(
    size: Long,
    limit: Long,
) {
    if (size > limit) throw JmapException.ResponseTooLarge(limit)
}

/** RFC 6570 level 1. Encode each value before substitution, including slashes and URI delimiters. */
private fun expandDownload(
    template: String,
    accountId: String,
    blobId: String,
    type: String,
): String {
    val values = mapOf("accountId" to accountId, "blobId" to blobId, "type" to type, "name" to "avatar")
    val expanded =
        Regex("\\{([^{}]+)}").replace(template) { match ->
            values[match.groupValues[1]]?.encodeURLParameter()
                ?: throw JmapException.NotJmap("unknown download template variable")
        }
    if ('{' in expanded || '}' in expanded) throw JmapException.NotJmap("invalid download template")
    return expanded
}
