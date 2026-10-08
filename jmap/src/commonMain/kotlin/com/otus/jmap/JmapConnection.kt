package com.otus.jmap

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One signed-in server. Caches the session, refetches it when a response reports a new session
 * state (RFC 8620, section 3.4), and drops it after a failure so the next request starts fresh.
 */
public class JmapConnection(
    private val client: JmapClient,
    public val sessionUrl: String,
    private val auth: JmapAuth,
    private val trustedOrigins: Set<String> = emptySet(),
) {
    private val lock = Mutex()
    private var cached: JmapSession? = null

    public suspend fun session(): JmapSession =
        lock.withLock { cached ?: client.session(sessionUrl, auth, trustedOrigins).also { cached = it } }

    /** One request; returns the result of the call [build] returns. */
    public suspend fun <T> request(build: JmapRequestBuilder.() -> JmapCall<T>): T {
        val builder = JmapRequestBuilder()
        val result = builder.build()
        return exchange(builder.calls)[result]
    }

    /** One request with a single call. */
    public suspend fun <T> call(method: JmapMethod<T>): T = request { call(method) }

    public suspend fun download(
        accountId: String,
        blobId: String,
        mediaType: String,
        maxBytes: Long,
    ): JmapBlob = client.download(session(), auth, accountId, blobId, mediaType, maxBytes)

    public suspend fun upload(
        accountId: String,
        bytes: ByteArray,
        mediaType: String,
    ): JmapUpload = client.upload(session(), auth, accountId, bytes, mediaType)

    /** One request; read any call's result from the response. */
    public suspend fun send(build: JmapRequestBuilder.() -> Unit): JmapResponse =
        exchange(JmapRequestBuilder().apply(build).calls)

    private suspend fun exchange(calls: List<JmapCall<*>>): JmapResponse {
        val session = session()
        val response =
            try {
                client.send(session, auth, calls)
            } catch (error: JmapException) {
                // Method errors surface only when a result is read, so this is a transport or session failure.
                invalidate()
                throw error
            }
        if (response.isOutdated(session)) invalidate()
        return response
    }

    public suspend fun invalidate() {
        lock.withLock { cached = null }
    }
}
