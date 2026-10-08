package com.otus.mail.feature.folders.data

import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapException
import com.otus.jmap.JmapType
import com.otus.jmap.SetError
import com.otus.mail.feature.folders.domain.RuleFailure
import kotlinx.serialization.Serializable

/** SieveScript objects (RFC 9661, section 2). */
internal val SieveScriptType: JmapType<SieveScriptObject> =
    JmapType("SieveScript", JmapCapability.SIEVE, SieveScriptObject.serializer())

internal const val SIEVE_MEDIA_TYPE = "application/sieve"

@Serializable
internal data class SieveScriptObject(
    val id: String,
    val name: String? = null,
    val blobId: String = "",
    val isActive: Boolean = false,
)

/** The account's Sieve capability (RFC 9661, section 1.3). */
@Serializable
internal data class SieveCapability(
    val sieveExtensions: List<String> = emptyList(),
    val maxSizeScript: Long? = null,
)

internal fun SetError.toRuleFailure(): RuleFailure =
    when (type) {
        "invalidSieve" -> RuleFailure.Invalid

        "tooLarge" -> RuleFailure.TooLarge

        "overQuota" -> RuleFailure.TooMany

        // Someone created or removed the script since it was loaded.
        "alreadyExists", "notFound" -> RuleFailure.ChangedElsewhere

        "forbidden" -> RuleFailure.NotPermitted

        else -> RuleFailure.ServerProblem
    }

internal fun JmapException.toRuleFailure(): RuleFailure =
    when {
        this is JmapException.MethodError && type == "stateMismatch" -> RuleFailure.ChangedElsewhere
        this is JmapException.Unauthorized -> RuleFailure.SignInRejected
        this is JmapException.Forbidden -> RuleFailure.NotPermitted
        this is JmapException.CredentialsUnavailable -> RuleFailure.CredentialsUnavailable
        this is JmapException.Unreachable -> RuleFailure.Unreachable
        this is JmapException.UploadTooLarge -> RuleFailure.TooLarge
        else -> RuleFailure.ServerProblem
    }
