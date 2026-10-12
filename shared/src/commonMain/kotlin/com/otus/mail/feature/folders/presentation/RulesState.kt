package com.otus.mail.feature.folders.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.folders.domain.AccountRules
import com.otus.mail.feature.folders.domain.ApplyOutcome
import com.otus.mail.feature.folders.domain.FolderStorageException
import com.otus.mail.feature.folders.domain.MailRule
import com.otus.mail.feature.folders.domain.RuleChange
import com.otus.mail.feature.folders.domain.RuleOutcome
import com.otus.mail.feature.folders.domain.RuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Each account's rules. Every change rewrites that account's whole script from the loaded rules;
 * a refused save reloads them, so the user can repeat the change on the current rules.
 */
class RulesState(
    private val repository: RuleRepository,
    private val scope: CoroutineScope,
) {
    var accounts by mutableStateOf(emptyList<AccountRules>())
        private set
    var loading by mutableStateOf(false)
        private set

    /** The outcome of the last change, worded for the user. */
    var notice by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    /** A change waiting for the user to replace edits made in another app; see [submit]. */
    var replaceRequest by mutableStateOf<ReplaceRequest?>(null)
        private set
    private var job: Job? = null

    fun account(accountId: String): AccountRules? = accounts.firstOrNull { it.accountId == accountId }

    fun refresh() {
        job?.cancel()
        job = scope.launch { load() }
    }

    fun clearNotice() {
        notice = null
    }

    /** Rewrites [accountId]'s rules with [transform]; whether they run stays as it is. */
    fun updateRules(
        accountId: String,
        transform: (List<MailRule>) -> List<MailRule>,
    ) = submit(accountId) { RuleChange(transform(it.rules), it.keep) }

    /**
     * Makes the rules the script the server runs. With [keepOther], the script running now keeps
     * running after the rules; without it, that script and any script kept before stop. A vacation
     * reply is never kept: its script stays on the server while the reply is off, so including it
     * would send replies the user turned off.
     */
    fun turnOn(
        accountId: String,
        keepOther: Boolean,
    ) = submit(accountId) { account ->
        val keep =
            when {
                account.activeOther == null -> account.keep
                keepOther && !account.vacationIsOn -> account.activeOther
                else -> null
            }
        RuleChange(account.rules, keep, run = true)
    }

    fun turnOff(accountId: String) = submit(accountId) { RuleChange(it.rules, it.keep, run = false) }

    /** Stops running the other script after the rules. */
    fun stopKeeping(accountId: String) = submit(accountId) { RuleChange(it.rules, keep = null) }

    /**
     * Saves the change [build] makes to [accountId]'s loaded rules; [onSaved] runs only when it
     * worked. When the script was edited in another app, the change waits in [replaceRequest],
     * unless [replacing] says the user already chose to replace those edits.
     */
    fun submit(
        accountId: String,
        onSaved: suspend () -> Unit = {},
        replacing: Boolean = false,
        build: (AccountRules) -> RuleChange,
    ) {
        val account = account(accountId)
        if (busy || account == null) return
        if (account.changedElsewhere && !replacing) {
            val close = { replaceRequest = null }
            replaceRequest =
                ReplaceRequest(
                    replace = {
                        close()
                        submit(accountId, onSaved, replacing = true, build)
                    },
                    dismiss = close,
                )
            return
        }
        busy = true
        notice = null
        scope.launch {
            try {
                when (val outcome = repository.save(account, build(account))) {
                    RuleOutcome.Saved -> onSaved()
                    is RuleOutcome.Failed -> notice = outcome.reason.message(outcome.detail)
                }
                job?.cancel()
                load()
            } catch (expected: FolderStorageException) {
                notice = STORAGE_NOTICE
            } finally {
                busy = false
            }
        }
    }

    /** Moves inbox mail [rule] matches and reports how much moved. */
    suspend fun applyToInbox(
        account: AccountRules,
        rule: MailRule,
    ) {
        val outcome =
            try {
                repository.applyToInbox(account, rule)
            } catch (expected: FolderStorageException) {
                null
            }
        notice =
            when (outcome) {
                is ApplyOutcome.Applied -> outcome.message()
                is ApplyOutcome.Failed -> "The rule is saved, but moving inbox mail failed. ${outcome.reason.message()}"
                null -> STORAGE_NOTICE
            }
    }

    private suspend fun load() {
        loading = true
        try {
            accounts = repository.accounts()
        } catch (expected: FolderStorageException) {
            notice = STORAGE_NOTICE
        } finally {
            loading = false
        }
    }
}

/** Asks before a change replaces rules edited in another app: [replace] saves it, [dismiss] drops it. */
class ReplaceRequest(
    val replace: () -> Unit,
    val dismiss: () -> Unit,
)
