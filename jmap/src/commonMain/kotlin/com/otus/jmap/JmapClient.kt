package com.otus.jmap

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Provider-independent JMAP core client (RFC 8620). One instance per app is enough; it is safe to
 * share. Pass [engine] in tests; otherwise the platform engine on the classpath is used.
 */
public class JmapClient(
    engine: HttpClientEngine? = null,
    private val policy: JmapPolicy = JmapPolicy(),
) {
    private val http = if (engine == null) HttpClient { configure() } else HttpClient(engine) { configure() }
    private val urls = JmapUrls(policy)

    /** Authenticated, bounded blob download (RFC 8620, section 6.2). No redirect receives credentials. */
    public suspend fun download(
        session: JmapSession,
        auth: JmapAuth,
        accountId: String,
        blobId: String,
        mediaType: String,
        maxBytes: Long,
    ): JmapBlob = JmapDownloads(http, urls, policy).download(session, auth, accountId, blobId, mediaType, maxBytes)

    /** Authenticated blob upload (RFC 8620, section 6.1), within the server's `maxSizeUpload`. */
    public suspend fun upload(
        session: JmapSession,
        auth: JmapAuth,
        accountId: String,
        bytes: ByteArray,
        mediaType: String,
    ): JmapUpload = JmapUploads(http, urls, policy).upload(session, auth, accountId, bytes, mediaType)

    /**
     * Fetches the session at [url], following redirects such as `/.well-known/jmap` (section 2.2).
     * Credentials only go to the origin of [url] and to [trustedOrigins]; a redirect elsewhere throws
     * [JmapException.RedirectedElsewhere] before any credentials are sent there.
     */
    public suspend fun session(
        url: String,
        auth: JmapAuth,
        trustedOrigins: Set<String> = emptySet(),
    ): JmapSession {
        var target = urls.parse(url)
        val trusted = trustedOrigins + target.origin()
        repeat(policy.maxRedirects + 1) {
            val authorized = target.origin() in trusted
            val response = exchange(HttpMethod.Get, target, auth.takeIf { authorized }, body = null)
            val location = response.location
            if (response.status in REDIRECT_STATUSES && location != null) {
                target = urls.resolve(target, location)
            } else {
                if (!authorized) throw JmapException.RedirectedElsewhere(target.host, target.origin())
                return parseSession(target, response, trusted)
            }
        }
        throw JmapException.TooManyRedirects()
    }

    /** Sends the calls built by [build] in one request (section 3.3) and returns the call [build] returns. */
    public suspend fun <T> request(
        session: JmapSession,
        auth: JmapAuth,
        build: JmapRequestBuilder.() -> JmapCall<T>,
    ): T {
        val builder = JmapRequestBuilder()
        val result = builder.build()
        return send(session, auth, builder.calls)[result]
    }

    /** Sends the calls built by [build] in one request; read any call's result from the response. */
    public suspend fun send(
        session: JmapSession,
        auth: JmapAuth,
        build: JmapRequestBuilder.() -> Unit,
    ): JmapResponse = send(session, auth, JmapRequestBuilder().apply(build).calls)

    internal suspend fun send(
        session: JmapSession,
        auth: JmapAuth,
        calls: List<JmapCall<*>>,
    ): JmapResponse {
        val using = linkedSetOf(JmapCapability.CORE) + calls.map { it.method.capability }
        checkSupported(session, calls, using)
        val body =
            buildJsonObject {
                put("using", buildJsonArray { using.forEach { add(JsonPrimitive(it)) } })
                put("methodCalls", buildJsonArray { calls.forEach { add(it.toJson()) } })
            }
        val api = urls.parse(session.apiUrl)
        if (api.origin() !in session.trustedOrigins) throw JmapException.RedirectedElsewhere(api.host, api.origin())
        val response = exchange(HttpMethod.Post, api, auth, body.toString())
        return jmapDecoding { parseResponse(decode(response)) }
    }

    private fun parseSession(
        url: Url,
        response: RawResponse,
        trusted: Set<String>,
    ): JmapSession {
        val dto = jmapDecoding { JmapJson.decodeFromJsonElement(SessionDto.serializer(), decode(response)) }
        val api = urls.resolve(url, dto.apiUrl)
        if (api.origin() !in trusted) throw JmapException.RedirectedElsewhere(api.host, api.origin())
        return JmapSession(
            sessionUrl = url.toString(),
            apiUrl = api.toString(),
            username = dto.username,
            capabilities = dto.capabilities.keys,
            accounts =
                dto.accounts.mapValues { (_, account) ->
                    JmapAccount(
                        account.name,
                        account.isPersonal,
                        account.isReadOnly,
                        account.accountCapabilities.keys,
                        account.accountCapabilities.capabilityObjects(),
                    )
                },
            primaryAccounts = dto.primaryAccounts,
            state = dto.state,
            trustedOrigins = trusted,
            downloadUrl = dto.downloadUrl,
            uploadUrl = dto.uploadUrl,
            capabilityObjects = dto.capabilities.capabilityObjects(),
        )
    }

    private fun decode(response: RawResponse): JsonObject {
        response.failure()?.let { throw it }
        return jmapDecoding { JmapJson.parseToJsonElement(response.text).jsonObject }
    }

    private suspend fun exchange(
        method: HttpMethod,
        target: Url,
        auth: JmapAuth?,
        body: String?,
    ): RawResponse =
        try {
            http
                .prepareRequest {
                    this.method = method
                    url(target)
                    auth?.let { header(HttpHeaders.Authorization, it.header()) }
                    header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    body?.let {
                        contentType(ContentType.Application.Json)
                        setBody(it)
                    }
                }.execute { response ->
                    RawResponse(
                        response.status.value,
                        response.headers[HttpHeaders.Location],
                        response.limitedText(policy.maxResponseBytes),
                    )
                }
        } catch (error: IOException) {
            throw JmapException.Unreachable(error)
        }

    private fun HttpClientConfig<*>.configure() {
        followRedirects = false
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = policy.connectTimeoutMillis
            requestTimeoutMillis = policy.requestTimeoutMillis
        }
    }

    private companion object {
        val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
    }
}

/** Checks the session and each call's account before anything is sent. */
private fun checkSupported(
    session: JmapSession,
    calls: List<JmapCall<*>>,
    using: Set<String>,
) {
    val missing =
        using.firstOrNull { it !in session.capabilities }
            ?: calls.firstOrNull { it.lacksAccountCapability(session) }?.method?.capability
    if (missing != null) throw JmapException.Unsupported(missing)
}

private fun JmapCall<*>.lacksAccountCapability(session: JmapSession): Boolean {
    val accountId = method.accountId ?: return false
    return method.capability !in session.accounts[accountId]?.capabilities.orEmpty()
}

private fun JmapCall<*>.toJson() =
    buildJsonArray {
        add(JsonPrimitive(method.name))
        add(method.arguments())
        add(JsonPrimitive(id))
    }
