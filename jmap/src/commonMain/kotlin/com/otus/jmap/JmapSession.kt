package com.otus.jmap

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Capability URIs this suite uses. Servers differ: always check [JmapSession.accountFor]. */
public object JmapCapability {
    public const val CORE: String = "urn:ietf:params:jmap:core"
    public const val MAIL: String = "urn:ietf:params:jmap:mail"
    public const val SUBMISSION: String = "urn:ietf:params:jmap:submission"
    public const val CALENDARS: String = "urn:ietf:params:jmap:calendars"
    public const val CONTACTS: String = "urn:ietf:params:jmap:contacts"
}

/**
 * The parts of the session resource (RFC 8620, section 2) this client uses. URLs are absolute and
 * already checked against the security policy and the trusted origins.
 */
public data class JmapSession(
    val sessionUrl: String,
    val apiUrl: String,
    val username: String,
    val capabilities: Set<String>,
    val accounts: Map<String, JmapAccount>,
    val primaryAccounts: Map<String, String>,
    val state: String,
    /** Origins that may receive credentials for this session. */
    val trustedOrigins: Set<String>,
    /** RFC 8620 level-1 URI template; expanded and checked only when a blob is requested. */
    val downloadUrl: String? = null,
) {
    /** The primary account for [capability], else the first account offering it, else null. */
    public fun accountFor(capability: String): String? =
        primaryAccounts[capability]?.takeIf { accounts[it]?.capabilities?.contains(capability) == true }
            ?: accounts.entries.firstOrNull { capability in it.value.capabilities }?.key
}

public data class JmapAccount(
    val name: String,
    val isPersonal: Boolean,
    val isReadOnly: Boolean,
    val capabilities: Set<String>,
)

/** Lenient decoding: servers add properties and capabilities over time. */
public val JmapJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

@Serializable
internal class SessionDto(
    val capabilities: JsonObject,
    val accounts: Map<String, AccountDto>,
    val primaryAccounts: Map<String, String> = emptyMap(),
    val username: String = "",
    val apiUrl: String,
    val state: String = "",
    val downloadUrl: String? = null,
)

@Serializable
internal class AccountDto(
    val name: String = "",
    val isPersonal: Boolean = false,
    val isReadOnly: Boolean = false,
    val accountCapabilities: JsonObject = JsonObject(emptyMap()),
)
