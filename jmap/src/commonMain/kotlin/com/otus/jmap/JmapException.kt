package com.otus.jmap

/**
 * Every failure caused by a server, the network, or stored data. Callers can rely on this: nothing a
 * server sends makes the client throw anything else. Messages never contain secrets.
 */
public sealed class JmapException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** HTTP 401: the credentials were rejected. */
    public class Unauthorized : JmapException("The server rejected the credentials.")

    /** HTTP 403: the credentials are valid, but this access is not allowed. */
    public class Forbidden : JmapException("The server does not allow this access.")

    /** The address answered, but not with usable JMAP data. */
    public class NotJmap(
        detail: String,
        cause: Throwable? = null,
    ) : JmapException("Not a JMAP response: $detail", cause)

    /** DNS, connection, TLS, or timeout failure. */
    public class Unreachable(
        cause: Throwable,
    ) : JmapException("The server could not be reached.", cause)

    /** A URL that is not https, or not a URL. Plain HTTP is refused unless [JmapPolicy.allowInsecureHttp]. */
    public class InsecureUrl(
        detail: String,
        cause: Throwable? = null,
    ) : JmapException("Refused URL: $detail", cause)

    /**
     * Credentials would go to [host], which the user did not enter. Ask the user, then retry with
     * [origin] in the trusted origins.
     */
    public class RedirectedElsewhere(
        public val host: String,
        public val origin: String,
    ) : JmapException("The server sends sign-in to another host: $host")

    public class TooManyRedirects : JmapException("Too many redirects.")

    public class ResponseTooLarge(
        public val limitBytes: Long,
    ) : JmapException("The response exceeded $limitBytes bytes.")

    /** The blob is larger than the server's advertised `maxSizeUpload`; nothing was sent. */
    public class UploadTooLarge(
        public val limitBytes: Long,
    ) : JmapException("The upload exceeds the server's $limitBytes-byte limit.")

    /** The server or account lacks a capability the request needs. */
    public class Unsupported(
        public val capability: String,
    ) : JmapException("Not supported by this server or account: $capability")

    /** A request-level problem (RFC 8620, section 3.6.1), or another HTTP failure status. */
    public class RequestFailed(
        public val status: Int,
        public val type: String?,
    ) : JmapException("The request failed with HTTP $status${type?.let { " ($it)" }.orEmpty()}.")

    /** A method-level error (RFC 8620, section 3.6.2), such as `stateMismatch` or `forbidden`. */
    public class MethodError(
        public val type: String,
        public val description: String?,
    ) : JmapException("The server rejected a method call: $type")

    /** The saved secret for a login is missing or cannot be decrypted; the user must sign in again. */
    public class CredentialsUnavailable(
        public val loginId: String,
    ) : JmapException("The saved credentials for this login cannot be read.")
}
