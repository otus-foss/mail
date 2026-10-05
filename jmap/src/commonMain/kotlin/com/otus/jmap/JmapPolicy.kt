package com.otus.jmap

/** Security and resource limits. The defaults are the production policy. */
public class JmapPolicy(
    /** Only for local test servers. Production code must never enable this. */
    public val allowInsecureHttp: Boolean = false,
    public val maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
    public val maxRedirects: Int = DEFAULT_MAX_REDIRECTS,
    public val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    public val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
) {
    private companion object {
        const val DEFAULT_MAX_RESPONSE_BYTES = 16L * 1024 * 1024
        const val DEFAULT_MAX_REDIRECTS = 5
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 15_000L
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 30_000L
    }
}
