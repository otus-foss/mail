package com.otusfoss.mail.feature.folders.domain

import com.otusfoss.mail.core.mailbox.FolderKey
import com.otusfoss.mail.core.storage.StoreSpec
import com.otusfoss.mail.core.storage.TextCodec

/**
 * The folders the color nudge has already offered a color for, whether the user picked one or
 * closed it. The nudge never offers these again.
 */
data class FolderNudges(
    val folders: Set<FolderKey> = emptySet(),
) {
    operator fun contains(folder: FolderKey): Boolean = folder in folders

    fun with(folder: FolderKey): FolderNudges = FolderNudges(folders + folder)

    /** Each login's mailbox ids, as [FolderNudgeStore] keeps them. */
    fun byLogin(): Map<String, List<String>> = folders.groupBy({ it.accountId }, { it.mailboxId })

    companion object {
        fun of(byLogin: Map<String, List<String>>): FolderNudges =
            FolderNudges(byLogin.flatMap { (accountId, ids) -> ids.map { FolderKey(accountId, it) } }.toSet())
    }
}

/**
 * App-private storage for [FolderNudges]: each account's mailbox ids, one per line, under its
 * login id. Login ids may hold spaces and colons, and JMAP ids never hold a line break.
 */
object FolderNudgeStore : StoreSpec<String, List<String>>("folder_nudges", TextCodec.Text, TextCodec.Lines)
