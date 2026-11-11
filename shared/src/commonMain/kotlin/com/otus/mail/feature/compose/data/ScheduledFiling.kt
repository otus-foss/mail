package com.otus.mail.feature.compose.data

import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/**
 * Runs [fileScheduled] one sweep at a time: launch, refreshes, and pushes each start one, and two
 * at once could both make a Scheduled folder. It remembers the accounts whose server refused the
 * folder, so later sweeps don't ask again while the process runs; an unreachable server is asked
 * again.
 */
internal class ScheduledFiling {
    private val sweep = Mutex()
    private val refused = mutableSetOf<String>()

    /** Files [account]'s scheduled mail among its [folders] at [now]; nothing without Sent. */
    suspend fun file(
        account: MailAccount,
        folders: List<MailFolder>,
        now: Instant,
    ) = sweep.withLock {
        val sent = folders.firstOrNull { it.role == MailboxRole.Sent }?.id ?: return@withLock
        account.fileScheduled(
            drafts = folders.firstOrNull { it.role == MailboxRole.Drafts }?.id,
            scheduled = folders.scheduledFolder()?.id,
            sent = sent,
            now = now,
        ) { folderFor(account, folders) }
    }

    private suspend fun folderFor(
        account: MailAccount,
        folders: List<MailFolder>,
    ): String? {
        val key = "${account.login.id}/${account.id}"
        if (key in refused) return null
        return account.makeScheduledFolder(folders.adoptableScheduled()).also { if (it == null) refused += key }
    }
}
