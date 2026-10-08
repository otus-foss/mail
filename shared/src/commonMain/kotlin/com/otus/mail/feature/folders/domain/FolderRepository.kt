package com.otus.mail.feature.folders.domain

enum class FolderFailure {
    AlreadyExists,
    NotPermitted,
    Invalid,
    HasSubfolders,
    HasMail,
    NotFound,
    NoMail,
    SignInRejected,
    CredentialsUnavailable,
    Unreachable,
    ServerProblem,
}

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
 * Folders on each connected account's server. Every change goes to the server; nothing is kept
 * locally. Any call can throw [FolderStorageException] when saved accounts cannot be read.
 */
interface FolderRepository {
    suspend fun accounts(): List<AccountFolders>

    suspend fun create(
        accountId: String,
        name: String,
        parentId: String?,
    ): FolderOutcome

    suspend fun rename(
        folder: MailFolder,
        name: String,
    ): FolderOutcome

    /** Moves [folder] under [parentId], or to the top level when it is null. */
    suspend fun move(
        folder: MailFolder,
        parentId: String?,
    ): FolderOutcome

    /** Deletes an empty folder without subfolders; the server refuses otherwise and no mail is lost. */
    suspend fun delete(folder: MailFolder): FolderOutcome
}

/** Saved account metadata could not be read. Folders already shown should stay. */
class FolderStorageException(
    cause: Throwable,
) : Exception("Folder account storage is unavailable.", cause)
