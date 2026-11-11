package com.otusfoss.mail.feature.folders.domain

import com.otusfoss.mail.core.mailbox.AccountFolders
import com.otusfoss.mail.core.mailbox.FolderFailure
import com.otusfoss.mail.core.mailbox.MailFolder

enum class FolderNameProblem { Empty, TooLong, Taken }

sealed interface FolderOutcome {
    /** [folderId] is the created or changed folder. */
    data class Done(
        val folderId: String,
    ) : FolderOutcome

    data class Failed(
        val reason: FolderFailure,
    ) : FolderOutcome
}

/**
 * Folders on each connected account's server. Every change goes to the server; the device keeps
 * only the last loaded folders, to show before the server answers. Any call can throw
 * [FolderStorageException] when saved accounts cannot be read.
 */
interface FolderRepository {
    suspend fun accounts(): List<AccountFolders>

    /**
     * Each account's own folders as the device kept them from the last load, without asking the
     * server. Shared mailboxes and server limits are not kept, and an account never loaded has no
     * folders.
     */
    suspend fun cachedAccounts(): List<AccountFolders> = emptyList()

    suspend fun create(
        accountId: String,
        name: String,
        parentId: String?,
    ): FolderOutcome

    suspend fun rename(
        folder: MailFolder,
        name: String,
    ): FolderOutcome

    /** Saves a dragged folder's new parent and the `sortOrder` numbers [placement] changes, in one request. */
    suspend fun place(placement: FolderPlacement): FolderOutcome

    /** Deletes an empty folder without subfolders; the server refuses otherwise and no mail is lost. */
    suspend fun delete(folder: MailFolder): FolderOutcome
}

/** Saved account metadata could not be read. Folders already shown should stay. */
class FolderStorageException(
    cause: Throwable,
) : Exception("Folder account storage is unavailable.", cause)
