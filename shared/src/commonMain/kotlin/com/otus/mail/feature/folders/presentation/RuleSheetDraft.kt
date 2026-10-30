package com.otus.mail.feature.folders.presentation

import com.otus.mail.feature.folders.domain.AddressField
import com.otus.mail.feature.folders.domain.AddressMatch
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.TextField
import com.otus.mail.feature.folders.domain.TextOperator

/**
 * The kinds of rule the Add rule sheet makes. Every Sieve server can run them, so the sheet always
 * offers all four; [field] is the address a From or To rule tests.
 */
enum class RuleKind(
    val label: String,
    val field: AddressField?,
    val placeholder: String,
) {
    From("From", AddressField.From, "Search senders…"),
    To("To", AddressField.To, "Search recipients…"),
    Subject("Subject", null, "Subject contains…"),
    MailingList("Mailing list", null, "List ID, such as releases.github.example"),
}

/**
 * What the Add rule sheet opens with: the [kind], the search text, and the [chosen] condition.
 * [editing] is the index of the folder's rule it changes; null adds a rule.
 */
data class RuleSheetDraft(
    val kind: RuleKind = RuleKind.From,
    val query: String = "",
    val chosen: RuleCondition? = null,
    val editing: Int? = null,
) {
    companion object {
        /** A new rule for mail from [address], as "Rule for sender" starts it. */
        fun from(address: String): RuleSheetDraft =
            RuleSheetDraft(
                RuleKind.From,
                address,
                RuleCondition.Address(AddressField.From, AddressMatch.Exact(address)),
            )
    }
}

/**
 * The sheet that edits the folder's rule at [index]; null when the sheet cannot show it whole,
 * such as a group, a header, or "isn't from", which only the delete action handles.
 */
fun FolderRule.sheetDraft(index: Int): RuleSheetDraft? {
    val condition = conditions.singleOrNull()?.takeUnless { it.negated }
    val start =
        when (condition) {
            is RuleCondition.Address -> condition.sheetDraft()
            is RuleCondition.Text -> condition.sheetDraft()
            is RuleCondition.MailingList -> RuleSheetDraft(RuleKind.MailingList, condition.listId)
            else -> null
        }
    return start?.copy(chosen = condition, editing = index)
}

private fun RuleCondition.Address.sheetDraft(): RuleSheetDraft? =
    RuleKind.entries
        .firstOrNull { it.field == field }
        ?.let { RuleSheetDraft(it, match.typed()) }

private fun RuleCondition.Text.sheetDraft(): RuleSheetDraft? =
    RuleSheetDraft(RuleKind.Subject, value).takeIf { field == TextField.Subject && operator == TextOperator.Contains }

/** The search text that offers [this] match again. */
private fun AddressMatch.typed(): String =
    when (this) {
        is AddressMatch.Exact -> address
        is AddressMatch.Domain -> "@$domain"
        is AddressMatch.LocalPart -> "$local@"
    }

/**
 * What the Add rule sheet does: [existingCount] counts the Inbox and Archive mail a rule matches,
 * [onSave] saves the rule and says whether to move that mail too, and [onDelete], set while it
 * edits a rule, deletes it.
 */
class RuleSheetActions(
    val existingCount: suspend (FolderRule) -> Int?,
    val onSave: (FolderRule, moveExisting: Boolean) -> Unit,
    val onDismiss: () -> Unit,
    val onDelete: (() -> Unit)? = null,
)
