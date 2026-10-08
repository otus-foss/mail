package com.otus.jmap

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.ByteArrayContent
import kotlinx.io.IOException
import kotlinx.serialization.Serializable

/** A blob the server stored (RFC 8620, section 6.1). Reference it by [blobId] in later calls. */
public data class JmapUpload(
    val blobId: String,
    val type: String,
    val size: Long,
)

internal class JmapUploads(
    private val http: HttpClient,
    private val urls: JmapUrls,
    private val policy: JmapPolicy,
) {
    suspend fun upload(
        session: JmapSession,
        auth: JmapAuth,
        accountId: String,
        bytes: ByteArray,
        mediaType: String,
    ): JmapUpload {
        require(accountId in session.accounts)
        checkUploadSize(session, bytes.size)
        val target = uploadTarget(session, accountId)
        val response =
            try {
                http
                    .preparePost(target) {
                        header(HttpHeaders.Authorization, auth.header())
                        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                        setBody(ByteArrayContent(bytes, ContentType.parse(mediaType)))
                    }.execute { RawResponse(it.status.value, null, it.limitedText(policy.maxResponseBytes)) }
            } catch (error: IOException) {
                throw JmapException.Unreachable(error)
            }
        // Redirects are not followed, so a moved endpoint fails here without resending the blob.
        response.failure()?.let { throw it }
        val dto = jmapDecoding { JmapJson.decodeFromString(UploadDto.serializer(), response.text) }
        return JmapUpload(dto.blobId, dto.type, dto.size)
    }

    private fun uploadTarget(
        session: JmapSession,
        accountId: String,
    ) = urls.trustedTarget(
        session,
        expandUriTemplate(
            session.uploadUrl ?: throw JmapException.NotJmap("no blob upload endpoint"),
            mapOf("accountId" to accountId),
        ),
    )
}

/** Refuses before sending anything the server would reject for size. */
private fun checkUploadSize(
    session: JmapSession,
    size: Int,
) {
    val limit = session.maxSizeUpload ?: return
    if (size > limit) throw JmapException.UploadTooLarge(limit)
}

@Serializable
private class UploadDto(
    val blobId: String,
    val type: String = "",
    val size: Long = 0,
)
