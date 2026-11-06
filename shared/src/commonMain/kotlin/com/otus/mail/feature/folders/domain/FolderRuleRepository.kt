package com.otus.mail.feature.folders.domain

import com.otus.mail.core.mail.MailFailure

/** What the account's server can run, from its advertised Sieve extensions (RFC 9661, section 1.3). */
data class RuleSupport(
    val extensions: Set<String>,
    /** The largest script the server stores, in octets; null when unlimited. */
    val maxScriptBytes: Long? = null,
) {
    /** Filing by folder id (RFC 9042) keeps rules working after a folder is renamed or moved. */
    val filesById: Boolean
        get() = MAILBOX_ID in extensions

    val canMatchBody: Boolean
        get() = "body" in extensions

    /** Arrival day and time (RFC 5260); a time range compares with `:value` (RFC 5231). */
    val canMatchTime: Boolean
        get() = "date" in extensions && RELATIONAL in extensions

    val canMatchSpam: Boolean
        get() = ("spamtest" in extensions || "spamtestplus" in extensions) && RELATIONAL in extensions

    fun supports(rule: FolderRule): Boolean = rule.conditions.all(::supports)

    fun supports(condition: RuleCondition): Boolean =
        when (condition) {
            is RuleCondition.Text -> condition.field != TextField.Body || canMatchBody
            is RuleCondition.When -> canMatchTime
            is RuleCondition.Spam -> canMatchSpam
            else -> true
        }

    private companion object {
        const val MAILBOX_ID = "mailboxid"
        const val RELATIONAL = "relational"
    }
}

/** Whether an account's server can run rules, and why not. */
sealed interface RuleAvailability {
    data class Available(
        val support: RuleSupport,
    ) : RuleAvailability

    /** No `urn:ietf:params:jmap:sieve`, or no `fileinto`: the folder page hides its Rules row. */
    data object RulesUnavailable : RuleAvailability

    data class Failed(
        val reason: RuleFailure,
    ) : RuleAvailability
}

/**
 * The Sieve script that holds the Otus block. [scriptId] is null when the account has no script yet
 * and saving creates "Otus Mail". [state] is the `SieveScript` state the rules were read in; a save
 * names it in `ifInState`, so a change made meanwhile elsewhere is refused instead of overwritten.
 */
data class RuleScriptFacts(
    val scriptId: String?,
    val scriptName: String,
    /** The server runs this script now. Saving activates it otherwise. */
    val active: Boolean,
    val hasBlock: Boolean,
    /** The block was edited in another app; saving replaces those edits, so the UI asks first. */
    val changedElsewhere: Boolean,
    /**
     * The rules came from the old "Otus Mail" script (`# otus-rules-v1`); the next save writes
     * them into the block.
     */
    val migrated: Boolean,
    val state: String,
)

/** One account's folder rules: for each folder id, its rules in folder order. */
data class AccountFolderRules(
    val accountId: String,
    val accountName: String,
    val availability: RuleAvailability,
    val rules: Map<String, List<FolderRule>> = emptyMap(),
    val script: RuleScriptFacts? = null,
) {
    val support: RuleSupport?
        get() = (availability as? RuleAvailability.Available)?.support

    val changedElsewhere: Boolean
        get() = script?.changedElsewhere == true

    fun rulesFor(folderId: String): List<FolderRule> = rules[folderId].orEmpty()
}

sealed interface RuleFailure {
    /** Another device or app changed the script first. */
    data object ChangedElsewhere : RuleFailure

    /** The server's validation refused the script; the outcome carries its explanation. */
    data object Invalid : RuleFailure

    data object TooLarge : RuleFailure

    data object TooMany : RuleFailure

    data object NotFound : RuleFailure

    /**
     * Stalwart's vacation reply is the active script. It is reserved and cannot be edited over
     * JMAP, so rules need the vacation reply turned off first.
     */
    data object VacationActive : RuleFailure

    data class Mail(
        val failure: MailFailure,
    ) : RuleFailure
}

sealed interface RuleOutcome {
    data object Saved : RuleOutcome

    /** [detail] is the server's explanation, such as a Sieve syntax error. */
    data class Failed(
        val reason: RuleFailure,
        val detail: String? = null,
    ) : RuleOutcome
}

/**
 * Rules live in a marked block of each account's active Sieve script, so they run on the server
 * while the app is closed. Any call can throw [FolderStorageException] when saved accounts cannot
 * be read.
 */
interface FolderRuleRepository {
    suspend fun accounts(): List<AccountFolderRules>

    /** Replaces [account]'s whole block with [rules], keyed by folder id. */
    suspend fun save(
        account: AccountFolderRules,
        rules: Map<String, List<FolderRule>>,
    ): RuleOutcome

    /**
     * How many messages in the Inbox and Archive [rule] matches, for "Move the N that are already
     * here"; null when JMAP cannot filter by its conditions or the server cannot be read.
     */
    suspend fun existingCount(
        accountId: String,
        folderId: String,
        rule: FolderRule,
    ): Int?

    /** Moves the Inbox and Archive mail [rule] matches into [folderId]; how many moved, or null on failure. */
    suspend fun moveExisting(
        accountId: String,
        folderId: String,
        rule: FolderRule,
    ): Int?
}

/** [rule] added as the folder's last rule. */
fun Map<String, List<FolderRule>>.withAdded(
    folderId: String,
    rule: FolderRule,
): Map<String, List<FolderRule>> = this + (folderId to get(folderId).orEmpty() + rule)

/** The folder's rule at [index] replaced with [rule]; unchanged when there is none. */
fun Map<String, List<FolderRule>>.withReplaced(
    folderId: String,
    index: Int,
    rule: FolderRule,
): Map<String, List<FolderRule>> =
    this + (folderId to get(folderId).orEmpty().mapIndexed { at, old -> if (at == index) rule else old })

/** The folder without its rule at [index]; a folder left without rules drops out. */
fun Map<String, List<FolderRule>>.withRemoved(
    folderId: String,
    index: Int,
): Map<String, List<FolderRule>> {
    val left = get(folderId).orEmpty().filterIndexed { at, _ -> at != index }
    return if (left.isEmpty()) this - folderId else this + (folderId to left)
}
