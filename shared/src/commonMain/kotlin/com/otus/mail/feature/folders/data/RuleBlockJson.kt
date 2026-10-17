package com.otus.mail.feature.folders.data

import com.otus.mail.feature.folders.domain.AddressField
import com.otus.mail.feature.folders.domain.AddressMatch
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.HeaderOperator
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.TextField
import com.otus.mail.feature.folders.domain.TextOperator
import com.otus.mail.feature.folders.domain.TimeRange
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The rules as one JSON line in the block, so the app reads them back without parsing Sieve. */
internal fun blockJson(block: RuleBlock): String =
    json.encodeToString(
        BlockDto.serializer(),
        BlockDto(
            block.zone,
            block.folders.map { folder ->
                FolderDto(folder.id, folder.path, folder.rules.map { it.toDto() })
            },
        ),
    )

/** Null when the line is not valid, or a newer app version wrote something this one does not know. */
internal fun parseBlockJson(line: String): RuleBlock? {
    val dto =
        try {
            json.decodeFromString(BlockDto.serializer(), line)
        } catch (expected: SerializationException) {
            null
        } catch (expected: IllegalArgumentException) {
            null
        }
    return dto?.toBlock()
}

private fun BlockDto.toBlock(): RuleBlock? {
    val read = folders.map { it.toFolder() }
    return if (read.any { it == null }) null else RuleBlock(read.filterNotNull(), zone)
}

private fun FolderDto.toFolder(): BlockFolder? {
    val read = rules.map { conditions -> conditions.map { it.toCondition() } }
    val complete = read.none { conditions -> conditions.any { it == null } }
    return if (complete) BlockFolder(id, path, read.map { FolderRule(it.filterNotNull()) }) else null
}

/** Compact and lenient: one line, and fields a newer version adds are ignored. */
private val json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        classDiscriminator = "kind"
    }

@Serializable
private class BlockDto(
    val zone: String = UTC,
    val folders: List<FolderDto> = emptyList(),
)

@Serializable
private class FolderDto(
    val id: String,
    val path: String,
    val rules: List<List<ConditionDto>>,
)

/** Stored names are fixed, so renaming Kotlin values never changes saved scripts. */
@Serializable
private sealed class ConditionDto {
    @Serializable
    @SerialName("address")
    class Address(
        val field: String,
        val match: String,
        val value: String,
        val not: Boolean = false,
    ) : ConditionDto()

    @Serializable
    @SerialName("text")
    class Text(
        val field: String,
        val op: String,
        val value: String,
        val not: Boolean = false,
    ) : ConditionDto()

    @Serializable
    @SerialName("header")
    class Header(
        val name: String,
        val op: String,
        val value: String,
        val not: Boolean = false,
    ) : ConditionDto()

    @Serializable
    @SerialName("list")
    class MailingList(
        val id: String,
        val not: Boolean = false,
    ) : ConditionDto()

    @Serializable
    @SerialName("size")
    class Size(
        val over: Boolean,
        val bytes: Long,
    ) : ConditionDto()

    /** [days] are ISO day numbers, Monday 1 to Sunday 7; times are `HH:MM`. */
    @Serializable
    @SerialName("when")
    class When(
        val days: List<Int> = emptyList(),
        val start: String? = null,
        val end: String? = null,
        val outside: Boolean = false,
    ) : ConditionDto()

    @Serializable
    @SerialName("spam")
    class Spam(
        val score: Int,
    ) : ConditionDto()
}

private fun FolderRule.toDto(): List<ConditionDto> = conditions.map { it.toDto() }

private fun RuleCondition.toDto(): ConditionDto =
    when (this) {
        is RuleCondition.Address -> {
            ConditionDto.Address(field.wire, match.wire, match.value, negated)
        }

        is RuleCondition.Text -> {
            ConditionDto.Text(field.wire, operator.wire, value, negated)
        }

        is RuleCondition.Header -> {
            ConditionDto.Header(name, operator.wire, value, negated)
        }

        is RuleCondition.MailingList -> {
            ConditionDto.MailingList(listId, negated)
        }

        is RuleCondition.Size -> {
            ConditionDto.Size(over, bytes)
        }

        is RuleCondition.When -> {
            ConditionDto.When(
                days.map { it.ordinal + 1 }.sorted(),
                range?.start?.toString(),
                range?.end?.toString(),
                outside,
            )
        }

        is RuleCondition.Spam -> {
            ConditionDto.Spam(minScore)
        }
    }

/** Null for a value this version does not know. */
private fun ConditionDto.toCondition(): RuleCondition? =
    when (this) {
        is ConditionDto.Address -> {
            AddressField.entries.wired(field)?.let { field ->
                addressMatch(match, value)?.let { RuleCondition.Address(field, it, not) }
            }
        }

        is ConditionDto.Text -> {
            TextField.entries.wired(field)?.let { field ->
                TextOperator.entries.wired(op)?.let { RuleCondition.Text(field, it, value, not) }
            }
        }

        is ConditionDto.Header -> {
            HeaderOperator.entries.wired(op)?.let { RuleCondition.Header(name, it, value, not) }
        }

        is ConditionDto.MailingList -> {
            RuleCondition.MailingList(id, not)
        }

        is ConditionDto.Size -> {
            RuleCondition.Size(over, bytes)
        }

        is ConditionDto.When -> {
            toWhen()
        }

        is ConditionDto.Spam -> {
            RuleCondition.Spam(score)
        }
    }

private fun ConditionDto.When.toWhen(): RuleCondition.When? {
    val dayValues = days.map { number -> DayOfWeek.entries.getOrNull(number - 1) }
    val time = { text: String -> runCatching { LocalTime.parse(text) }.getOrNull() }
    val from = start?.let(time)
    val until = end?.let(time)
    val unreadable = dayValues.any { it == null } || (start != null && from == null) || (end != null && until == null)
    val range = if (from != null && until != null) TimeRange(from, until) else null
    return if (unreadable) null else RuleCondition.When(dayValues.filterNotNull().toSet(), range, outside)
}

private fun addressMatch(
    kind: String,
    value: String,
): AddressMatch? =
    when (kind) {
        "exact" -> AddressMatch.Exact(value)
        "domain" -> AddressMatch.Domain(value)
        "local" -> AddressMatch.LocalPart(value)
        else -> null
    }

private val AddressMatch.wire: String
    get() =
        when (this) {
            is AddressMatch.Exact -> "exact"
            is AddressMatch.Domain -> "domain"
            is AddressMatch.LocalPart -> "local"
        }

private val Enum<*>.wire: String
    get() = name.replaceFirstChar { it.lowercase() }

private fun <E : Enum<E>> List<E>.wired(value: String): E? = firstOrNull { it.wire == value }

/** The zone a block without one uses. */
internal const val UTC = "+0000"
