package com.otus.mail.feature.folders.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.folders.domain.RuleChange
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.RuleField
import com.otus.mail.feature.folders.domain.RuleOperator
import com.otus.mail.feature.folders.domain.upserted

/** The rule being edited. It is kept when a save fails, so the user can save it again. */
class RuleEditorState(
    private val rules: RulesState,
) {
    var draft by mutableStateOf<RuleDraft?>(null)
        private set

    /** Starts editing [ruleId], or a new rule when it is null or no longer exists. */
    fun edit(
        accountId: String,
        ruleId: String?,
    ) {
        val rule = ruleId?.let { rules.account(accountId)?.rule(it) }
        draft = rule?.let { RuleDraft.of(accountId, it) } ?: RuleDraft(accountId, null)
        rules.clearNotice()
    }

    /** Starts a new rule for mail from [address]; the user still chooses where it goes. */
    fun startForSender(
        accountId: String,
        address: String,
    ) {
        draft = RuleDraft(accountId, null, conditions = listOf(RuleCondition(RuleField.From, RuleOperator.Is, address)))
        rules.clearNotice()
    }

    fun update(value: RuleDraft) {
        draft = value
    }

    fun close() {
        draft = null
    }

    /**
     * Saves the draft into the loaded rules, then moves matching inbox mail when asked. A first
     * rule turns the rules on unless another script runs: replacing that is the user's choice.
     */
    fun save(onSaved: () -> Unit) {
        val current = draft ?: return
        val rule = current.toRule()
        rules.submit(
            current.accountId,
            onSaved = {
                draft = null
                rules.account(current.accountId)?.let { if (current.applyToInbox) rules.applyToInbox(it, rule) }
                onSaved()
            },
        ) { account ->
            val run = true.takeIf { !account.installed && account.activeOther == null }
            RuleChange(account.rules.upserted(rule), account.keep, run)
        }
    }
}
