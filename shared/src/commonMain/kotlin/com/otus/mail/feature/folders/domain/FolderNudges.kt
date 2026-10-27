package com.otus.mail.feature.folders.domain

import com.otus.mail.core.preferences.StringMapStore

/**
 * The folders the color nudge has already offered a color for, whether the user picked one or
 * closed it. The nudge never offers these again.
 */
data class FolderNudges(
    val folders: Set<FolderKey> = emptySet(),
) {
    operator fun contains(folder: FolderKey): Boolean = folder in folders

    fun with(folder: FolderKey): FolderNudges = FolderNudges(folders + folder)

    fun withoutAccount(accountId: String): FolderNudges =
        FolderNudges(folders.filterNot { it.accountId == accountId }.toSet())
}

/**
 * App-private storage for [FolderNudges]: each account's mailbox ids, one per line, under its
 * login id. Login ids may hold spaces and colons, and JMAP ids never hold a line break.
 */
interface FolderNudgeStore : StringMapStore

fun FolderNudgeStore.readNudges(): FolderNudges =
    FolderNudges(
        read()
            .flatMap { (accountId, lines) ->
                lines.lines().filter(String::isNotBlank).map { FolderKey(accountId, it) }
            }.toSet(),
    )

fun FolderNudgeStore.writeNudges(nudges: FolderNudges) =
    write(
        nudges.folders
            .groupBy({ it.accountId }, { it.mailboxId })
            .mapValues { (_, ids) -> ids.joinToString("\n") },
    )

/** Forgets a removed account's nudges. */
fun FolderNudgeStore.forgetAccount(accountId: String) = writeNudges(readNudges().withoutAccount(accountId))
