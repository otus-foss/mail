package com.otus.mail.feature.notifications.presentation

import com.otus.accounts.domain.ConnectedAccount
import com.otus.designsystem.components.InfoText
import com.otus.mail.feature.notifications.domain.NotificationPreviews
import com.otus.mail.feature.notifications.domain.PreviewChoices

internal val NotificationPreviews.title: String
    get() =
        when (this) {
            NotificationPreviews.Device -> "Use device setting"
            NotificationPreviews.Hidden -> "New mail only"
        }

internal val NotificationPreviews.short: String
    get() =
        when (this) {
            NotificationPreviews.Device -> "Device setting"
            NotificationPreviews.Hidden -> "New mail only"
        }

internal val NotificationPreviews.description: String
    get() =
        when (this) {
            NotificationPreviews.Device -> {
                "Sender and subject; the lock screen follows your phone"
            }

            NotificationPreviews.Hidden -> {
                "Locked or unlocked"
            }
        }

/** Whose previews a choice sheet sets: one login's mail, or mail sent to one address. */
internal sealed interface PreviewTarget {
    val title: String
    val same: String

    fun current(choices: PreviewChoices): NotificationPreviews?

    fun apply(
        choices: PreviewChoices,
        choice: NotificationPreviews?,
    ): PreviewChoices

    data class Login(
        val loginId: String,
        val name: String,
    ) : PreviewTarget {
        override val title = name
        override val same = "Same as all accounts"

        override fun current(choices: PreviewChoices) = choices.logins[loginId]

        override fun apply(
            choices: PreviewChoices,
            choice: NotificationPreviews?,
        ) = choices.withLogin(loginId, choice)
    }

    data class Address(
        val address: String,
        val loginId: String,
        val account: String,
    ) : PreviewTarget {
        override val title = address
        override val same = "Same as $account"

        override fun current(choices: PreviewChoices) = choices.addresses[address.lowercase()]

        override fun apply(
            choices: PreviewChoices,
            choice: NotificationPreviews?,
        ) = choices.withAddress(address, choice)
    }
}

/** The user's own addresses of [account]: its identities, else its sign-in name when that is one. */
internal val ConnectedAccount.addresses: List<String>
    get() = emailAddresses.ifEmpty { listOf(username).filter { '@' in it } }.distinctBy { it.lowercase() }

/**
 * How many of [accounts] and their addresses have a choice of their own, such as "1 account and
 * 2 addresses use their own setting"; null when none do, so the row stays quiet.
 */
internal fun PreviewChoices.overrideSummary(accounts: List<ConnectedAccount>): String? {
    val ownLogins = accounts.count { it.id in logins }
    val ownAddresses = accounts.flatMap { it.addresses }.count { it.lowercase() in addresses }
    val parts =
        listOfNotNull(
            (if (ownLogins == 1) "1 account" else "$ownLogins accounts").takeIf { ownLogins > 0 },
            (if (ownAddresses == 1) "1 address" else "$ownAddresses addresses").takeIf { ownAddresses > 0 },
        )
    if (parts.isEmpty()) return null
    val verb = if (ownLogins + ownAddresses == 1) "uses its" else "use their"
    return "${parts.joinToString(" and ")} $verb own setting"
}

/** What the Previews choices mean, behind the section's info button. */
internal val PreviewsInfo =
    InfoText(
        "Previews",
        listOf(
            "Use device setting shows the sender and subject. On the lock screen, your phone's own " +
                "notification setting decides whether they show, only \"New mail\" shows, or nothing does.",
            "New mail only never shows the sender, subject, or account, locked or unlocked.",
            "Accounts and addresses can each have their own setting. An address's setting wins over " +
                "its account's, and mail sent to several of your addresses follows the more private one.",
        ),
    )
