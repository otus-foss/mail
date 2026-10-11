package com.otus.mail.feature.folders.presentation.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.PageNote
import com.otus.designsystem.components.PageSection
import com.otus.designsystem.components.Subpage
import com.otus.designsystem.elements.ActionButton
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.AccountRules
import com.otus.mail.feature.folders.domain.RuleAvailability
import com.otus.mail.feature.folders.domain.folderTree
import com.otus.mail.feature.folders.domain.moved
import com.otus.mail.feature.folders.domain.paused
import com.otus.mail.feature.folders.domain.without
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.RulesDialog
import com.otus.mail.feature.folders.presentation.RulesState
import com.otus.mail.feature.folders.presentation.components.RuleRow
import com.otus.mail.feature.folders.presentation.components.RulesActions
import com.otus.mail.feature.folders.presentation.components.RulesDialogs
import com.otus.mail.feature.folders.presentation.message
import com.otus.mail.feature.folders.presentation.statusText

/** Each account's rules, in the order the server tries them, and whether they run. */
@Composable
fun RulesScreen(
    state: RulesState,
    folders: FoldersState,
    onBack: () -> Unit,
    onEdit: (accountId: String, ruleId: String?) -> Unit,
) {
    var dialog by rememberSaveable(stateSaver = RulesDialog.Saver) { mutableStateOf<RulesDialog?>(null) }
    LaunchedEffect(Unit) {
        state.refresh()
        folders.refresh()
    }
    Subpage("Rules", onBack) {
        PageNote("Rules move arriving mail into folders. They run on your mail server, in order; the first match wins.")
        state.notice?.let { PageNote(it) }
        if (state.accounts.isEmpty()) {
            PageNote(if (state.loading) "Loading rules…" else "Connect an account in Settings to add rules.")
        }
        state.accounts.forEach { account ->
            PageSection(account.accountName) {
                AccountRulesContent(account, folders.account(account.accountId), state, onEdit) { dialog = it }
            }
        }
    }
    dialog?.let { open ->
        val actions =
            RulesActions(
                onEdit = onEdit,
                onMove = { accountId, ruleId, offset -> state.updateRules(accountId) { it.moved(ruleId, offset) } },
                onPause = { accountId, ruleId, paused -> state.updateRules(accountId) { it.paused(ruleId, paused) } },
                onDelete = { accountId, ruleId -> state.updateRules(accountId) { it.without(ruleId) } },
                onTurnOn = state::turnOn,
            )
        RulesDialogs(open, state.account(open.accountId), actions) { dialog = it }
    }
}

@Composable
private fun AccountRulesContent(
    account: AccountRules,
    folders: AccountFolders?,
    state: RulesState,
    onEdit: (accountId: String, ruleId: String?) -> Unit,
    onDialog: (RulesDialog) -> Unit,
) {
    when (val availability = account.availability) {
        RuleAvailability.NoSieve -> {
            PageNote("This server doesn't offer rules over JMAP. Search filters in the inbox still work.")
        }

        RuleAvailability.NoFolderRules -> {
            PageNote("This server's rules can't file mail into folders by ID, which Otus Mail needs.")
        }

        is RuleAvailability.Failed -> {
            PageNote(availability.reason.message())
        }

        is RuleAvailability.Available -> {
            account.statusText()?.let { PageNote(it) }
            if (account.changedElsewhere) {
                PageNote("These rules were edited in another app. Saving here replaces those edits.")
            }
            account.keep?.let { keep ->
                PageNote("After your rules, “$keep” runs too.")
                ActionButton("Stop running “$keep”", { state.stopKeeping(account.accountId) }, Modifier.fillMaxWidth())
            }
            val paths =
                folders
                    ?.let {
                        it.folderTree().associate { node ->
                            node.folder.id to node.path
                        }
                    }.orEmpty()
            account.rules.forEach { rule ->
                RuleRow(rule, paths[rule.folderId]) { onDialog(RulesDialog.Actions(account.accountId, rule.id)) }
            }
            ActionButton(
                "New rule",
                { onEdit(account.accountId, null) },
                Modifier.fillMaxWidth(),
                primary = true,
                enabled = !state.busy,
            )
            RunButton(account, state, onDialog)
        }
    }
}

/** Turning rules on asks first when they would replace a script that runs now. */
@Composable
private fun RunButton(
    account: AccountRules,
    state: RulesState,
    onDialog: (RulesDialog) -> Unit,
) {
    if (!account.installed || account.rules.isEmpty()) return
    if (account.running) {
        ActionButton(
            "Turn rules off",
            { state.turnOff(account.accountId) },
            Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )
    } else {
        ActionButton(
            "Turn rules on",
            {
                if (account.activeOther == null) {
                    state.turnOn(account.accountId, keepOther = false)
                } else {
                    onDialog(RulesDialog.TurnOn(account.accountId))
                }
            },
            Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )
    }
}
