package com.otus.mail.feature.folders.presentation

import com.otus.mail.feature.folders.domain.FolderFailure
import com.otus.mail.feature.folders.domain.RuleFailure

/** Why rules could not be saved or loaded, worded for the user. [detail] is the server's explanation. */
fun RuleFailure.message(detail: String? = null): String =
    when (this) {
        RuleFailure.ChangedElsewhere -> {
            "The rules changed on another device. They're reloaded; save again."
        }

        RuleFailure.Invalid -> {
            "The server refused these rules${detail?.let { ": ${it.trimEnd('.')}" }.orEmpty()}. Nothing was changed."
        }

        RuleFailure.TooLarge -> {
            "These rules are too large for this server. Remove some."
        }

        RuleFailure.TooMany -> {
            "The server can't store another script for this account."
        }

        RuleFailure.NotPermitted -> {
            "The server doesn't allow this account to change rules."
        }

        RuleFailure.NotFound -> {
            "That account is no longer connected."
        }

        RuleFailure.VacationActive -> {
            "Your vacation reply is on. Turn it off to use rules; the server runs one of them at a time."
        }

        RuleFailure.SignInRejected -> {
            FolderFailure.SignInRejected.message()
        }

        RuleFailure.CredentialsUnavailable -> {
            FolderFailure.CredentialsUnavailable.message()
        }

        RuleFailure.Unreachable -> {
            "Couldn't reach the server. Try again."
        }

        RuleFailure.ServerProblem -> {
            "The server returned an error. Try again."
        }
    }
