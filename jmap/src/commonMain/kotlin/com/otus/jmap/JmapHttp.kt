package com.otus.jmap

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.URLBuilder
import io.ktor.http.URLParserException
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentLength
import io.ktor.http.encodeURLParameter
import io.ktor.http.takeFrom
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Parses and resolves URLs, refusing anything the policy does not allow. */
internal class JmapUrls(
    private val policy: JmapPolicy,
) {
    fun parse(text: String): Url = checked(build(text) { takeFrom(text) }, text)

    fun resolve(
        base: Url,
        reference: String,
    ): Url = checked(build(reference) { takeFrom(base).takeFrom(reference) }, reference)

    /** Resolves an expanded session template, refusing origins that may not receive credentials. */
    fun trustedTarget(
        session: JmapSession,
        expanded: String,
    ): Url {
        val target = resolve(parse(session.sessionUrl), expanded)
        if (target.origin() !in session.trustedOrigins) {
            throw JmapException.RedirectedElsewhere(target.host, target.origin())
        }
        return target
    }

    private fun build(
        text: String,
        block: URLBuilder.() -> Unit,
    ): Url =
        try {
            URLBuilder().apply(block).build()
        } catch (error: URLParserException) {
            throw JmapException.InsecureUrl(text, error)
        }

    private fun checked(
        url: Url,
        text: String,
    ): Url {
        val secure = url.protocol == URLProtocol.HTTPS || (policy.allowInsecureHttp && url.protocol == URLProtocol.HTTP)
        if (url.host.isBlank() || !secure) throw JmapException.InsecureUrl(text)
        return url
    }
}

/** Credentials are scoped to scheme, host, and port. */
internal fun Url.origin(): String = "${protocol.name}://${host.lowercase()}:$port"

internal class RawResponse(
    val status: Int,
    val location: String?,
    val text: String,
) {
    /** The failure for a non-success status; a problem-details body (RFC 7807) adds its type. */
    fun failure(): JmapException? =
        when (status) {
            in SUCCESS -> null
            UNAUTHORIZED -> JmapException.Unauthorized()
            FORBIDDEN -> JmapException.Forbidden()
            NOT_FOUND -> JmapException.NotJmap("nothing found at this address")
            else -> JmapException.RequestFailed(status, problemType())
        }

    private fun problemType(): String? =
        runCatching { JmapJson.parseToJsonElement(text).jsonObject }.getOrNull()?.let(JsonObject::problemTypeOf)

    private companion object {
        val SUCCESS = 200..299
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
    }
}

private fun JsonObject.problemTypeOf(): String? = string("type")

/** Reads at most [limit] bytes; a larger body throws instead of exhausting memory. */
internal suspend fun HttpResponse.limitedText(limit: Long): String {
    if ((contentLength() ?: 0) > limit) throw JmapException.ResponseTooLarge(limit)
    val bytes = bodyAsChannel().readBuffer(limit + 1).readByteArray()
    if (bytes.size > limit) throw JmapException.ResponseTooLarge(limit)
    return bytes.decodeToString()
}

/** RFC 6570 level 1. Encode each value before substitution, including slashes and URI delimiters. */
internal fun expandUriTemplate(
    template: String,
    values: Map<String, String>,
): String {
    val expanded =
        Regex("\\{([^{}]+)}").replace(template) { match ->
            values[match.groupValues[1]]?.encodeURLParameter()
                ?: throw JmapException.NotJmap("unknown URI template variable")
        }
    if ('{' in expanded || '}' in expanded) throw JmapException.NotJmap("invalid URI template")
    return expanded
}
