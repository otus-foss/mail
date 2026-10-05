package com.otus.mail.feature.accounts.domain

/** A connected account as the app shows it. */
data class ConnectedAccount(
    val id: String,
    val name: String,
    val username: String,
    val server: String,
)

enum class ConnectFailure {
    WrongCredentials,
    NotPermitted,
    InvalidUsername,
    NotJmap,
    NoMail,
    Unreachable,
    Insecure,
    ServerError,
    StorageFailed,
}

sealed interface ConnectOutcome {
    data class Connected(
        val account: ConnectedAccount,
    ) : ConnectOutcome

    data class Failed(
        val reason: ConnectFailure,
    ) : ConnectOutcome

    /** Sign-in would go to [host], which the user did not enter. Retry with [origin] trusted to continue. */
    data class ConfirmHost(
        val host: String,
        val origin: String,
    ) : ConnectOutcome
}

interface AccountRepository {
    suspend fun accounts(): List<ConnectedAccount>

    /** Verifies the server and credentials before anything is saved. */
    suspend fun connect(draft: JmapAccountDraft): ConnectOutcome

    suspend fun remove(id: String)
}

/** A local account operation failed; callers may retry without treating it as a server rejection. */
class AccountStorageException(
    cause: Throwable,
) : Exception("Account storage is unavailable.", cause)
