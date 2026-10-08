package com.otus.mail.feature.folders.data

import com.otus.mail.feature.folders.domain.MailRule
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.RuleField
import com.otus.mail.feature.folders.domain.RuleOperator
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The script name on every server. RFC 9661 requires names to be unique per account. */
internal const val RULES_SCRIPT = "Otus Mail"

/** The rules and the other script that runs after them, as one Sieve script stores them. */
internal data class RuleScript(
    val rules: List<MailRule>,
    val keep: String? = null,
)

/** The rules read back from a script. [changedElsewhere] when its Sieve no longer matches them. */
internal data class ReadRules(
    val script: RuleScript,
    val changedElsewhere: Boolean,
)

/** The full script for [script], with the rules as one JSON comment line the app can read back. */
internal fun encodeRules(script: RuleScript): String {
    val dto = ScriptDto(script.rules.map { it.toDto() }, script.keep)
    return writeSieve(script, METADATA_PREFIX + json.encodeToString(ScriptDto.serializer(), dto))
}

/**
 * Reads the rules from [text]. The Sieve is regenerated from them and compared, so an edit made
 * in another app is reported instead of being silently replaced. A script without readable
 * rules yields none and counts as changed elsewhere.
 */
internal fun decodeRules(text: String): ReadRules {
    val line = text.lineSequence().map { it.trimEnd('\r') }.firstOrNull { it.startsWith(METADATA_PREFIX) }
    val dto =
        line?.let {
            try {
                json.decodeFromString(ScriptDto.serializer(), it.removePrefix(METADATA_PREFIX))
            } catch (expected: SerializationException) {
                null
            } catch (expected: IllegalArgumentException) {
                null
            }
        }
    val rules = dto?.rules?.map { it.toRule() }
    if (dto == null || rules == null || rules.any { it == null }) return ReadRules(RuleScript(emptyList()), true)
    val script = RuleScript(rules.filterNotNull(), dto.keep)
    return ReadRules(script, normalized(encodeRules(script)) != normalized(text))
}

private const val METADATA_PREFIX = "# otus-rules-v1 "

/** Compact and lenient: one line, and fields a newer version adds are ignored. */
private val json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

/** Servers may store other line ends or trailing spaces; neither is an edit. */
private fun normalized(text: String): String = text.lines().joinToString("\n") { it.trimEnd() }.trimEnd()

@Serializable
private class ScriptDto(
    val rules: List<RuleDto>,
    val keep: String? = null,
)

@Serializable
private class RuleDto(
    val id: String,
    val name: String = "",
    val conditions: List<ConditionDto>,
    val folder: String,
    val matchAll: Boolean = true,
    val markRead: Boolean = false,
    val enabled: Boolean = true,
)

@Serializable
private class ConditionDto(
    val field: String,
    val operator: String,
    val value: String,
)

private fun MailRule.toDto() =
    RuleDto(
        id,
        name,
        conditions.map {
            ConditionDto(it.field.wire, it.operator.wire, it.value)
        },
        folderId,
        matchAll,
        markRead,
        enabled,
    )

/** Null when a newer version wrote a condition this one does not know. */
private fun RuleDto.toRule(): MailRule? {
    val parsed =
        conditions.map { dto ->
            val field = RuleField.entries.firstOrNull { it.wire == dto.field }
            val operator = RuleOperator.entries.firstOrNull { it.wire == dto.operator }
            if (field == null || operator == null) null else RuleCondition(field, operator, dto.value)
        }
    if (parsed.any { it == null }) return null
    return MailRule(id, name, parsed.filterNotNull(), folder, matchAll, markRead, enabled)
}

/** Stored names are fixed, so renaming Kotlin values never changes saved scripts. */
private val RuleField.wire: String
    get() =
        when (this) {
            RuleField.From -> "from"
            RuleField.To -> "to"
            RuleField.Cc -> "cc"
            RuleField.Recipient -> "recipient"
            RuleField.Subject -> "subject"
            RuleField.Body -> "body"
            RuleField.AttachmentType -> "attachmentType"
            RuleField.AttachmentName -> "attachmentName"
        }

private val RuleOperator.wire: String
    get() =
        when (this) {
            RuleOperator.Contains -> "contains"
            RuleOperator.DoesNotContain -> "doesNotContain"
            RuleOperator.Is -> "is"
            RuleOperator.IsNot -> "isNot"
            RuleOperator.StartsWith -> "startsWith"
            RuleOperator.EndsWith -> "endsWith"
            RuleOperator.MatchesRegex -> "matchesRegex"
        }
