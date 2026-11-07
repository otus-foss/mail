package com.otus.mail.feature.folders.data

import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapException
import com.otus.jmap.JmapJson
import com.otus.jmap.JmapMethod
import com.otus.jmap.JmapType
import com.otus.jmap.SetError
import com.otus.mail.core.mail.MailFailure
import com.otus.mail.core.mail.toMailFailure
import com.otus.mail.feature.folders.domain.RuleFailure
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

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

/**
 * `SieveScript/validate` (RFC 9661, section 2.4): checks an uploaded script without storing it.
 * The result is the server's objection, or null when the script is valid.
 */
internal class SieveValidateMethod(
    accountId: String,
    private val blobId: String,
) : JmapMethod<SetError?>("SieveScript/validate", JmapCapability.SIEVE, accountId) {
    override fun arguments(): JsonObject =
        buildJsonObject {
            put("accountId", JsonPrimitive(accountId))
            put("blobId", JsonPrimitive(blobId))
        }

    override fun decode(arguments: JsonObject): SetError? =
        JmapJson.decodeFromJsonElement(ValidateDto.serializer(), arguments).error
}

@Serializable
private class ValidateDto(
    val error: SetError? = null,
)

internal fun SetError.toRuleFailure(): RuleFailure =
    when (type) {
        // RFC 9661 says invalidSieve; Stalwart answers invalidScript.
        "invalidSieve", "invalidScript" -> RuleFailure.Invalid

        "tooLarge" -> RuleFailure.TooLarge

        "overQuota" -> RuleFailure.TooMany

        // Someone created or removed the script since it was loaded.
        "alreadyExists", "notFound" -> RuleFailure.ChangedElsewhere

        "forbidden" -> RuleFailure.Mail(MailFailure.NotPermitted)

        else -> RuleFailure.Mail(MailFailure.ServerProblem)
    }

internal fun JmapException.toRuleFailure(): RuleFailure =
    when {
        this is JmapException.MethodError && type == "stateMismatch" -> RuleFailure.ChangedElsewhere
        this is JmapException.UploadTooLarge -> RuleFailure.TooLarge
        else -> toMailFailure().toRuleFailure()
    }

// A missing capability here is Sieve's, not mail's, so it reads as a server problem.
private fun MailFailure.toRuleFailure(): RuleFailure =
    RuleFailure.Mail(if (this == MailFailure.NoMail) MailFailure.ServerProblem else this)
