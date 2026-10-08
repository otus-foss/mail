package com.otus.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.otus.designsystem.components.ActionDialog
import com.otus.designsystem.components.DialogAction
import com.otus.mail.feature.folders.domain.AccountRules
import com.otus.mail.feature.folders.presentation.RulesDialog
import com.otus.mail.feature.folders.presentation.title

/** Shows [dialog]; one whose account or rule disappeared after a reload closes. */
@Composable
fun RulesDialogs(
    dialog: RulesDialog,
    account: AccountRules?,
    actions: RulesActions,
    onDialog: (RulesDialog?) -> Unit,
) {
    val close = { onDialog(null) }
    when {
        account == null -> {
            LaunchedEffect(dialog) { close() }
        }

        dialog is RulesDialog.TurnOn -> {
            TurnOnDialog(account, actions, close)
        }

        dialog is RulesDialog.Actions -> {
            RuleActionsDialog(account, dialog.ruleId, actions, onDialog)
        }

        dialog is RulesDialog.Delete -> {
            val rule = account.rule(dialog.ruleId)
            if (rule == null) {
                LaunchedEffect(dialog) { close() }
            } else {
                ActionDialog(
                    "Delete “${rule.title()}”?",
                    listOf(DialogAction("Delete", { actions.onDelete(account.accountId, rule.id) }, primary = true)),
                    close,
                    message = "Mail it already moved stays in its folder.",
                )
            }
        }
    }
}

@Composable
private fun RuleActionsDialog(
    account: AccountRules,
    ruleId: String,
    actions: RulesActions,
    onDialog: (RulesDialog?) -> Unit,
) {
    val rule = account.rule(ruleId)
    if (rule == null) {
        LaunchedEffect(ruleId) { onDialog(null) }
        return
    }
    val id = account.accountId
    val index = account.rules.indexOf(rule)
    val choices =
        listOfNotNull(
            DialogAction("Edit", { actions.onEdit(id, ruleId) }, primary = true),
            DialogAction("Move up", { actions.onMove(id, ruleId, -1) }).takeIf { index > 0 },
            DialogAction("Move down", { actions.onMove(id, ruleId, 1) }).takeIf { index < account.rules.lastIndex },
            DialogAction(if (rule.enabled) "Pause" else "Resume", { actions.onPause(id, ruleId, rule.enabled) }),
            DialogAction("Delete", { onDialog(RulesDialog.Delete(id, ruleId)) }),
        )
    ActionDialog(rule.title(), choices, onDismiss = { onDialog(null) })
}

/**
 * The server runs one script at a time. A vacation reply is only ever replaced, never kept: its
 * script stays on the server while the reply is off, so running it would send replies anyway.
 */
@Composable
private fun TurnOnDialog(
    account: AccountRules,
    actions: RulesActions,
    onDismiss: () -> Unit,
) {
    val other = account.activeOther ?: "another script"
    val id = account.accountId
    val support = account.support
    val (message, choices) =
        when {
            account.vacationIsOn -> {
                "Your vacation reply is on. This server runs one of them at a time, so turning rules on switches " +
                    "the vacation reply off." to
                    listOf(DialogAction("Turn rules on", { actions.onTurnOn(id, false) }, true))
            }

            support?.canKeepOtherScript == true -> {
                "“$other” runs on this server now. Keep running it for mail your rules don't move, or replace it." to
                    listOf(
                        DialogAction("Keep “$other” too", { actions.onTurnOn(id, true) }, primary = true),
                        DialogAction("Replace “$other”", { actions.onTurnOn(id, false) }),
                    )
            }

            else -> {
                "“$other” runs on this server now and will stop." to
                    listOf(DialogAction("Turn rules on", { actions.onTurnOn(id, false) }, primary = true))
            }
        }
    ActionDialog("Turn rules on?", choices, onDismiss, message = message)
}
