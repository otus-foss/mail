package com.otus.mail.feature.inbox.domain

enum class InboxFailure { SignInRejected, NotPermitted, CredentialsUnavailable, NoMail, Unreachable, ServerProblem }

data class UnavailableAccount(
    val name: String,
    val reason: InboxFailure,
)

/** Messages from every connected account, and the accounts that could not be loaded. */
data class InboxSnapshot(
    val messages: List<MailMessage>,
    val unavailable: List<UnavailableAccount> = emptyList(),
)

interface MailSource {
    suspend fun inbox(): InboxSnapshot
}

/** Saved account metadata could not be read. Existing inbox data should be retained. */
class InboxStorageException(
    cause: Throwable,
) : Exception("Inbox account storage is unavailable.", cause)
