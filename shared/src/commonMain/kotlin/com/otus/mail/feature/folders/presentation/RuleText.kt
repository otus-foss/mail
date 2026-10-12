package com.otus.mail.feature.folders.presentation

import com.otus.mail.feature.folders.domain.AccountRules
import com.otus.mail.feature.folders.domain.ApplyOutcome
import com.otus.mail.feature.folders.domain.FolderFailure
import com.otus.mail.feature.folders.domain.MailRule
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.RuleFailure
import com.otus.mail.feature.folders.domain.RuleField
import com.otus.mail.feature.folders.domain.RuleOperator
import com.otus.mail.feature.folders.domain.RuleProblem

val RuleField.label: String
    get() =
        when (this) {
            RuleField.From -> "From"
            RuleField.To -> "To"
            RuleField.Cc -> "Cc"
            RuleField.Recipient -> "To or Cc"
            RuleField.Subject -> "Subject"
            RuleField.Body -> "Body"
            RuleField.AttachmentType -> "Attachment type"
            RuleField.AttachmentName -> "Attachment name"
        }

val RuleField.example: String
    get() =
        when (this) {
            RuleField.From, RuleField.To, RuleField.Cc, RuleField.Recipient -> "name@example.com"
            RuleField.Subject -> "Invoice"
            RuleField.Body -> "unsubscribe"
            RuleField.AttachmentType -> "text/calendar"
            RuleField.AttachmentName -> ".ics"
        }

val RuleOperator.label: String
    get() =
        when (this) {
            RuleOperator.Contains -> "contains"
            RuleOperator.DoesNotContain -> "doesn't contain"
            RuleOperator.Is -> "is"
            RuleOperator.IsNot -> "isn't"
            RuleOperator.StartsWith -> "starts with"
            RuleOperator.EndsWith -> "ends with"
            RuleOperator.MatchesRegex -> "matches regex"
        }

fun RuleCondition.summary(): String = "${field.label} ${operator.label} “$value”"

/** The first condition, and how many more must or may match. */
fun MailRule.summary(): String {
    val first = conditions.firstOrNull()?.summary() ?: "No conditions"
    val more = conditions.size - 1
    return if (more == 0) first else "$first ${if (matchAll) "and" else "or"} $more more"
}

fun MailRule.title(): String = name.ifBlank { summary() }

fun RuleProblem.message(): String =
    when (this) {
        RuleProblem.NoConditions -> "Add at least one condition."
        RuleProblem.EmptyValue -> "Fill in every condition."
        RuleProblem.ControlCharacter -> "Remove the control characters from the name or conditions."
        RuleProblem.InvalidRegex -> "One regular expression isn't valid."
        RuleProblem.Unsupported -> "This server can't run one of these conditions or actions."
        RuleProblem.NoFolder -> "Choose a folder to move mail into."
    }

fun RuleFailure.message(detail: String? = null): String =
    when (this) {
        RuleFailure.ChangedElsewhere -> "The rules changed on another device. They're reloaded; save again."
        RuleFailure.Invalid -> "The server refused these rules${detail?.let { ": $it" } ?: "."}"
        RuleFailure.TooLarge -> "These rules are too large for this server. Remove or shorten some."
        RuleFailure.TooMany -> "The server can't store another script for this account."
        RuleFailure.NotPermitted -> "The server doesn't allow this account to change rules."
        RuleFailure.NotFound -> "That account is no longer connected."
        RuleFailure.SignInRejected -> FolderFailure.SignInRejected.message()
        RuleFailure.CredentialsUnavailable -> FolderFailure.CredentialsUnavailable.message()
        RuleFailure.Unreachable -> "Couldn't reach the server. Try again."
        RuleFailure.ServerProblem -> "The server returned an error. Try again."
    }

fun ApplyOutcome.Applied.message(): String {
    val moved = if (moved == 1) "Moved 1 message" else "Moved $moved messages"
    return if (more) "$moved from the newest $checked in the inbox." else "$moved from the inbox."
}

/** Whether the rules run, and what runs instead when they do not. */
fun AccountRules.statusText(): String? =
    when {
        running -> "Rules are on. The server runs them as mail arrives, even when this app is closed."
        vacationIsOn -> "Rules are off while your vacation reply is on. This server runs only one of them at a time."
        activeOther != null && installed -> "Rules are off: “$activeOther” runs instead."
        activeOther != null -> "“$activeOther” runs on this server. A new rule stays off until you turn rules on."
        installed -> "Rules are off."
        else -> null
    }
