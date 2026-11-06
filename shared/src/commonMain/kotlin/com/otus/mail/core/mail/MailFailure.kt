package com.otus.mail.core.mail

import com.otus.jmap.JmapErrorCategory
import com.otus.jmap.JmapException
import com.otus.jmap.category

/** Why a login's mail could not be read or changed, the same for every feature. */
enum class MailFailure { SignInRejected, NotPermitted, CredentialsUnavailable, NoMail, Unreachable, ServerProblem }

/** The one reading of a JMAP failure that every Mail feature starts from. */
internal fun JmapException.toMailFailure(): MailFailure =
    when (category) {
        JmapErrorCategory.Auth -> MailFailure.SignInRejected
        JmapErrorCategory.Permission -> MailFailure.NotPermitted
        JmapErrorCategory.Credentials -> MailFailure.CredentialsUnavailable
        JmapErrorCategory.Unsupported -> MailFailure.NoMail
        JmapErrorCategory.Offline -> MailFailure.Unreachable
        JmapErrorCategory.Server -> MailFailure.ServerProblem
    }
