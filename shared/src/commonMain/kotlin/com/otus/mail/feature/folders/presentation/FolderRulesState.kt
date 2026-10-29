package com.otus.mail.feature.folders.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.folders.domain.AccountFolderRules
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.FolderRuleRepository
import com.otus.mail.feature.folders.domain.FolderStorageException
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.RuleFailure
import com.otus.mail.feature.folders.domain.RuleOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Each account's folder rules. Every change rewrites the account's whole Otus block from the
 * loaded rules, and stays busy until the reload after it ends, so the next change builds on the
 * saved rules and their state. A refused save keeps the server's script and says why in [notice].
 */
class FolderRulesState(
    private val repository: FolderRuleRepository,
    private val scope: CoroutineScope,
) {
    var accounts by mutableStateOf(emptyList<AccountFolderRules>())
        private set
    var loading by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set

    /** Why the last change or load failed, worded for the user, such as the server's validation error. */
    var notice by mutableStateOf<String?>(null)
        private set

    /** A change waiting for the user to replace rules edited in another app; see [submit]. */
    var replaceRequest by mutableStateOf<ReplaceRequest?>(null)
        private set
    private val loads = LatestLoad(scope)

    fun account(accountId: String): AccountFolderRules? = accounts.firstOrNull { it.accountId == accountId }

    /** The folder's rules in order; empty while they load or when the server has none. */
    fun rules(folder: MailFolder): List<FolderRule> = account(folder.accountId)?.rulesFor(folder.id).orEmpty()

    fun refresh() {
        loads.start(::load)
    }

    fun dismissNotice() {
        notice = null
    }

    /** How many Inbox and Archive messages [rule] matches; null hides "Move the N that are already here". */
    suspend fun existingCount(
        folder: MailFolder,
        rule: FolderRule,
    ): Int? =
        try {
            repository.existingCount(folder.accountId, folder.id, rule)
        } catch (expected: FolderStorageException) {
            null
        }

    /** Moves the Inbox and Archive mail [rule] matches into [folder]; [onMoved] gets how many, or null on failure. */
    fun moveExisting(
        folder: MailFolder,
        rule: FolderRule,
        onMoved: (Int?) -> Unit = {},
    ) {
        scope.launch {
            val moved =
                try {
                    repository.moveExisting(folder.accountId, folder.id, rule)
                } catch (expected: FolderStorageException) {
                    null
                }
            if (moved == null) notice = MOVE_FAILED_NOTICE
            onMoved(moved)
        }
    }

    /**
     * Saves the rules [build] makes from [accountId]'s loaded rules; [onSaved] runs only when it
     * worked. When the block was edited in another app, the change waits in [replaceRequest] unless
     * [replacing] says the user already chose to replace those edits. A caller off the folder page
     * passes [onFailed]: it gets the reason whenever the change is not saved, and a change to rules
     * edited elsewhere is not saved, because only the folder page asks.
     */
    fun submit(
        accountId: String,
        onSaved: suspend () -> Unit = {},
        replacing: Boolean = false,
        onFailed: ((String) -> Unit)? = null,
        build: (Map<String, List<FolderRule>>) -> Map<String, List<FolderRule>>,
    ) {
        val account = account(accountId)
        val refusal =
            when {
                busy -> BUSY_NOTICE
                account?.support == null -> RuleFailure.NotFound.message()
                account.changedElsewhere && !replacing && onFailed != null -> EDITED_ELSEWHERE_NOTICE
                else -> null
            }
        if (refusal != null || account == null) {
            if (onFailed != null) refusal?.let(onFailed) else notice = refusal
            return
        }
        if (account.changedElsewhere && !replacing) {
            replaceRequest = ReplaceRequest(replace = { submit(accountId, onSaved, replacing = true, build = build) })
            return
        }
        replaceRequest = null
        save(account, build(account.rules), onSaved, onFailed)
    }

    fun dismissReplace() {
        replaceRequest = null
    }

    private fun save(
        account: AccountFolderRules,
        rules: Map<String, List<FolderRule>>,
        onSaved: suspend () -> Unit,
        onFailed: ((String) -> Unit)?,
    ) {
        busy = true
        notice = null
        scope.launch {
            try {
                when (val outcome = repository.save(account, rules)) {
                    RuleOutcome.Saved -> {
                        onSaved()
                    }

                    is RuleOutcome.Failed -> {
                        outcome.reason.message(outcome.detail).let { reason ->
                            notice = reason
                            onFailed?.invoke(reason)
                        }
                    }
                }
                refresh()
                loads.awaitLatest()
            } catch (expected: FolderStorageException) {
                notice = STORAGE_NOTICE
                onFailed?.invoke(STORAGE_NOTICE)
            } finally {
                busy = false
            }
        }
    }

    private suspend fun load(isLatest: () -> Boolean) {
        loading = true
        try {
            val loaded = repository.accounts()
            if (isLatest()) accounts = loaded
        } catch (expected: FolderStorageException) {
            if (isLatest()) notice = STORAGE_NOTICE
        } finally {
            // A replaced load can finish after the newer one started; only the newest ends loading.
            if (isLatest()) loading = false
        }
    }
}

/** Asks before a change replaces rules edited in another app; [replace] saves it anyway. */
class ReplaceRequest(
    val replace: () -> Unit,
)

private const val BUSY_NOTICE = "Rules are still saving. Try again."
private const val EDITED_ELSEWHERE_NOTICE =
    "These rules were edited in another app. Change them on the folder's page first."
private const val MOVE_FAILED_NOTICE = "The rule is saved, but moving the mail already here failed. Try again."
