package com.otus.mail.feature.sidebar.presentation

import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailScopeChoice
import com.otus.mail.core.messages.domain.addressCount
import com.otus.mail.core.settings.domain.SidebarCount

/**
 * The count beside a "Show mail from" [choice], by the [counts] setting. Accounts and All mail
 * count their inboxes in [accounts] on the server; an address counts the loaded [inboxMail] sent to it.
 */
fun scopeCount(
    choice: MailScopeChoice,
    counts: SidebarCount,
    inboxMail: List<MailMessage>,
    accounts: List<AccountFolders>,
): String? =
    when (choice) {
        is MailScopeChoice.Address -> addressCount(inboxMail, choice, counts)?.toString()
        else -> SidebarCounter(choice.accountId, counts).of(accounts.mapNotNull { it.inbox })
    }

/**
 * The counts beside sidebar mailboxes and folders: those of [accountId] alone, or of every
 * account when null. [count] is null while counts are off.
 */
internal class SidebarCounter(
    private val accountId: String?,
    private val count: ((MailFolder) -> Long)?,
) {
    constructor(accountId: String?, setting: SidebarCount) : this(
        accountId,
        when (setting) {
            // Drafts and Scheduled hold no unread mail, so they count what they hold.
            SidebarCount.Unread -> { folder -> if (folder.role?.holdsUnsent == true) folder.total else folder.unread }

            SidebarCount.Total -> MailFolder::total

            SidebarCount.None -> null
        },
    )

    /** Null when counts are off or there is nothing to count. */
    fun of(folders: List<MailFolder>): String? = sum(folders)?.toString()

    /** [of], capped so a large count fits a mailbox tile's badge. */
    fun badge(folders: List<MailFolder>): String? =
        sum(folders)?.let { if (it > BADGE_MAX) "$BADGE_MAX+" else it.toString() }

    private fun sum(folders: List<MailFolder>): Long? {
        val valueOf = count ?: return null
        return folders
            .filter { accountId == null || it.accountId == accountId }
            .sumOf(valueOf)
            .takeIf { it > 0 }
    }

    private companion object {
        const val BADGE_MAX = 999L
    }
}
