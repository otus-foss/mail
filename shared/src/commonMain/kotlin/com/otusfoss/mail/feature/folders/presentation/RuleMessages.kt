package com.otusfoss.mail.feature.folders.presentation

import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.feature.folders.domain.RuleFailure

/** Why rules could not be saved or loaded, worded for the user. [detail] is the server's explanation. */
fun RuleFailure.message(detail: String? = null): String =
    when (this) {
        RuleFailure.ChangedElsewhere -> "The rules changed on another device. They're reloaded; save again."
        RuleFailure.Invalid -> invalidRules(detail)
        RuleFailure.TooLarge -> "These rules are too large for this server. Remove some."
        RuleFailure.TooMany -> "The server can't store another script for this account."
        RuleFailure.NotFound -> "That account is no longer connected."
        RuleFailure.VacationActive -> VACATION_NOTICE
        is RuleFailure.Mail -> failure.ruleMessage()
    }

private fun MailFailure.ruleMessage(): String =
    when (this) {
        MailFailure.NotPermitted -> "The server doesn't allow this account to change rules."
        else -> message()
    }

private fun invalidRules(detail: String?): String =
    "The server refused these rules${detail?.let { ": ${it.trimEnd('.')}" }.orEmpty()}. Nothing was changed."

private const val VACATION_NOTICE =
    "Your vacation reply is on. Turn it off to use rules; the server runs one of them at a time."
