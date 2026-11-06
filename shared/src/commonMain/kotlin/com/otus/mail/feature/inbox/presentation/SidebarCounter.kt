package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.feature.inbox.domain.SidebarCount

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
            SidebarCount.Unread -> MailFolder::unread
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
