package com.otus.mail.feature.folders.domain

/** Whether an account's server can run rules, and why not. */
sealed interface RuleAvailability {
    data class Available(
        val support: RuleSupport,
    ) : RuleAvailability

    /** The server does not offer Sieve scripts over JMAP. Saved filters still work in the app. */
    data object NoSieve : RuleAvailability

    /** Sieve is offered, but not filing into folders by id. */
    data object NoFolderRules : RuleAvailability

    data class Failed(
        val reason: RuleFailure,
    ) : RuleAvailability
}

/**
 * One account's rules as its server stores them. The server runs one script at a time: [running]
 * is true when the rules' script is that one, and [activeOther] names any other script running.
 */
data class AccountRules(
    val accountId: String,
    val accountName: String,
    val availability: RuleAvailability,
    val rules: List<MailRule> = emptyList(),
    val installed: Boolean = false,
    val running: Boolean = false,
    val activeOther: String? = null,
    /** Another script that runs after the rules, for mail no rule moved. */
    val keep: String? = null,
    /** The script was edited outside this app; saving replaces those edits. */
    val changedElsewhere: Boolean = false,
    /** The server's state when loaded; a save based on an older state is refused. */
    val version: String? = null,
) {
    val support: RuleSupport?
        get() = (availability as? RuleAvailability.Available)?.support

    /** Stalwart turns its vacation reply into a script with this name, which then runs instead. */
    val vacationIsOn: Boolean
        get() = activeOther.equals(VACATION_SCRIPT, ignoreCase = true)

    fun rule(id: String): MailRule? = rules.firstOrNull { it.id == id }

    companion object {
        const val VACATION_SCRIPT = "vacation"
    }
}

/** A complete replacement for an account's rules. [run] turns them on or off; null leaves them as they are. */
data class RuleChange(
    val rules: List<MailRule>,
    val keep: String?,
    val run: Boolean? = null,
)

enum class RuleFailure {
    /** Another device or app changed the rules first. */
    ChangedElsewhere,
    Invalid,
    TooLarge,
    TooMany,
    NotPermitted,
    NotFound,
    SignInRejected,
    CredentialsUnavailable,
    Unreachable,
    ServerProblem,
}

sealed interface RuleOutcome {
    data object Saved : RuleOutcome

    /** [detail] is the server's explanation, such as a Sieve syntax error. */
    data class Failed(
        val reason: RuleFailure,
        val detail: String? = null,
    ) : RuleOutcome
}

sealed interface ApplyOutcome {
    /** [moved] of the newest [checked] inbox messages matched; [more] means older mail was not checked. */
    data class Applied(
        val moved: Int,
        val checked: Int,
        val more: Boolean,
    ) : ApplyOutcome

    data class Failed(
        val reason: RuleFailure,
    ) : ApplyOutcome
}

/**
 * Rules live in one script on each account's server, so they run while the app is closed. Any call
 * can throw [FolderStorageException] when saved accounts cannot be read.
 */
interface RuleRepository {
    suspend fun accounts(): List<AccountRules>

    suspend fun save(
        account: AccountRules,
        change: RuleChange,
    ): RuleOutcome

    /** Moves messages already in the inbox that [rule] matches. Rules alone only see new mail. */
    suspend fun applyToInbox(
        account: AccountRules,
        rule: MailRule,
    ): ApplyOutcome
}
