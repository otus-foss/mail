package com.otusfoss.mail.feature.folders.presentation

import com.otusfoss.core.designsystem.mail.folders.components.RuleSegment
import com.otusfoss.mail.core.text.sizeText
import com.otusfoss.mail.feature.folders.domain.AddressField
import com.otusfoss.mail.feature.folders.domain.AddressMatch
import com.otusfoss.mail.feature.folders.domain.FolderRule
import com.otusfoss.mail.feature.folders.domain.HeaderOperator
import com.otusfoss.mail.feature.folders.domain.RuleCondition
import com.otusfoss.mail.feature.folders.domain.TextField
import com.otusfoss.mail.feature.folders.domain.TextOperator

/** What one rule chip row shows: the label column, the avatar or icon slot, and the value. */
data class RuleChipText(
    val label: String,
    val slot: ChipSlot,
    val segments: List<RuleSegment>,
) {
    /** The value as plain text, for content descriptions. */
    val value: String
        get() = segments.joinToString("") { it.text }
}

sealed interface ChipSlot {
    /**
     * A person or a domain. [address] is set for one address, whose contact photo can stand in;
     * a domain has none and shows the first letter of [initialsFrom] on grey.
     */
    data class Avatar(
        val initialsFrom: String,
        val address: String?,
    ) : ChipSlot

    data class Icon(
        val icon: RuleIcon,
    ) : ChipSlot
}

/** The grey symbol in the slot, by the design system's symbol name. */
enum class RuleIcon { ShortText, Code, Forum, Notes, AttachFile, Schedule, CalendarMonth, Report, AlternateEmail }

/** One row per condition; a group shows them under one "All of" outline. */
fun FolderRule.chipTexts(): List<RuleChipText> = conditions.map { it.chipText() }

fun RuleCondition.chipText(): RuleChipText = chipParts().let { it.copy(segments = it.segments.merged()) }

private fun RuleCondition.chipParts(): RuleChipText =
    when (this) {
        is RuleCondition.Address -> addressChip()
        is RuleCondition.Text -> textChip()
        is RuleCondition.Header -> headerChip()
        is RuleCondition.MailingList -> mailingListChip()
        is RuleCondition.Size -> sizeChip()
        is RuleCondition.When -> whenChip()
        is RuleCondition.Spam -> spamChip()
    }

private fun RuleCondition.MailingList.mailingListChip(): RuleChipText =
    RuleChipText("List", ChipSlot.Icon(RuleIcon.Forum), negation("isn't ") + bright(listId))

private fun RuleCondition.Address.addressChip(): RuleChipText {
    val notWord = if (field == AddressField.From) "isn't from " else "isn't "
    val localPartIcon = ChipSlot.Icon(RuleIcon.AlternateEmail)
    val (slot, value) =
        when (match) {
            is AddressMatch.Exact -> ChipSlot.Avatar(match.address, match.address) to bright(match.address)
            is AddressMatch.Domain -> ChipSlot.Avatar(match.domain, address = null) to bright("@" + match.domain)
            is AddressMatch.LocalPart -> localPartIcon to bright(match.local + "@") + dim(" any domain")
        }
    return RuleChipText(field.name, slot, negation(notWord) + value)
}

private fun RuleCondition.Text.textChip(): RuleChipText {
    val words =
        when (operator) {
            TextOperator.Contains -> if (negated) "doesn't contain " else ""
            TextOperator.StartsWith -> if (negated) "doesn't start with " else "starts with "
            TextOperator.Matches -> if (negated) "doesn't match " else "matches "
        }
    val quoted = if (operator == TextOperator.Matches) wildcards(value) else bright(value)
    val icon = if (field == TextField.Subject) RuleIcon.ShortText else RuleIcon.Notes
    return RuleChipText(field.name, ChipSlot.Icon(icon), dim(words) + bright(OPEN_QUOTE) + quoted + bright(CLOSE_QUOTE))
}

private fun RuleCondition.Header.headerChip(): RuleChipText {
    val words =
        when (operator) {
            HeaderOperator.Is -> if (negated) " isn't " else " is "
            HeaderOperator.Contains -> if (negated) " doesn't contain " else " contains "
        }
    return RuleChipText("Header", ChipSlot.Icon(RuleIcon.Code), bright(name) + dim(words) + bright(value))
}

private fun RuleCondition.Size.sizeChip(): RuleChipText =
    RuleChipText(
        "Size",
        ChipSlot.Icon(RuleIcon.AttachFile),
        dim(if (over) "over " else "under ") + bright(sizeText(bytes)),
    )

private fun RuleCondition.Spam.spamChip(): RuleChipText =
    RuleChipText("Spam", ChipSlot.Icon(RuleIcon.Report), bright("Likely spam") + dim(" · score $minScore+"))

/** The words before the value when [RuleCondition.negated], dim. */
private fun RuleCondition.negation(words: String): List<RuleSegment> = if (negated) dim(words) else emptyList()

private const val OPEN_QUOTE = "“"
private const val CLOSE_QUOTE = "”"
