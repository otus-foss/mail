package com.otus.mail.feature.folders.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.folders.domain.AccountRules
import com.otus.mail.feature.folders.domain.ApplyOutcome
import com.otus.mail.feature.folders.domain.FolderStorageException
import com.otus.mail.feature.folders.domain.MailRule
import com.otus.mail.feature.folders.domain.RuleChange
import com.otus.mail.feature.folders.domain.RuleFailure
import com.otus.mail.feature.folders.domain.RuleOutcome
import com.otus.mail.feature.folders.domain.RuleRepository
import kotlinx.coroutines.CoroutineScope
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
    private val loads = LatestLoad(scope)

    fun account(accountId: String): AccountRules? = accounts.firstOrNull { it.accountId == accountId }

    fun refresh() {
        loads.start(::load)
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
     * unless [replacing] says the user already chose to replace those edits. A caller off the rules
     * page passes [onFailed] instead: it gets the reason, worded for the user, whenever the change
     * is not saved, and a change to edited rules is not saved, because only that page asks.
     */
    fun submit(
        accountId: String,
        onSaved: suspend () -> Unit = {},
        replacing: Boolean = false,
        onFailed: ((String) -> Unit)? = null,
        build: (AccountRules) -> RuleChange,
    ) {
        val account = account(accountId)
        val refusal = refusal(busy, account, offPage = !replacing && onFailed != null)
        if (refusal != null || account == null) {
            refusal?.let { onFailed?.invoke(it) }
            return
        }
        if (account.changedElsewhere && !replacing) {
            val close = { replaceRequest = null }
            replaceRequest =
                ReplaceRequest(
                    replace = {
                        close()
                        submit(accountId, onSaved, replacing = true, build = build)
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
                    RuleOutcome.Saved -> {
                        onSaved()
                    }

                    is RuleOutcome.Failed -> {
                        val reason = outcome.reason.message(outcome.detail)
                        notice = reason
                        onFailed?.invoke(reason)
                    }
                }
                // Busy lasts until the newest load ends, so the next change builds on current rules.
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

/** Asks before a change replaces rules edited in another app: [replace] saves it, [dismiss] drops it. */
class ReplaceRequest(
    val replace: () -> Unit,
    val dismiss: () -> Unit,
)

/**
 * Why a change cannot start, worded for the user; null when it can. A change asked for [offPage]
 * cannot replace rules edited in another app, because only the rules page asks about that.
 */
private fun refusal(
    busy: Boolean,
    account: AccountRules?,
    offPage: Boolean,
): String? =
    when {
        busy -> BUSY_NOTICE
        account == null -> RuleFailure.NotFound.message()
        account.changedElsewhere && offPage -> EDITED_ELSEWHERE_NOTICE
        else -> null
    }

private const val BUSY_NOTICE = "Rules are still saving. Try again."
private const val EDITED_ELSEWHERE_NOTICE = "The rules were edited in another app. Change them in Rules first."
