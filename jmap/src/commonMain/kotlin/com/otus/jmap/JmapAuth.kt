package com.otus.jmap

import kotlin.io.encoding.Base64

/** How requests authenticate. The secret is only ever turned into a header; it is never readable or printed. */
public sealed class JmapAuth {
    internal abstract fun header(): String

    /** HTTP Basic (RFC 7617) with an account password or an app password. */
    public class Basic(
        public val username: String,
        private val password: String,
    ) : JmapAuth() {
        init {
            // RFC 7617, section 2: the user-id cannot contain a colon.
            require(':' !in username) { "A Basic auth username cannot contain ':'" }
        }

        override fun header(): String = "Basic " + Base64.encode("$username:$password".encodeToByteArray())

        override fun toString(): String = "JmapAuth.Basic(username=$username, password=***)"
    }

    /** A bearer token (RFC 6750), such as an API token or an OAuth access token. */
    public class Bearer(
        private val token: String,
    ) : JmapAuth() {
        override fun header(): String = "Bearer $token"

        override fun toString(): String = "JmapAuth.Bearer(token=***)"
    }
}
