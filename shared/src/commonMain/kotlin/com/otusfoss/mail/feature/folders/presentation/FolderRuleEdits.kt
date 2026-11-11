package com.otusfoss.mail.feature.folders.presentation

import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.feature.folders.domain.FolderRule
import com.otusfoss.mail.feature.folders.domain.withAdded
import com.otusfoss.mail.feature.folders.domain.withRemoved
import com.otusfoss.mail.feature.folders.domain.withReplaced

/** Adds [rule] as [folder]'s last rule; [onSaved] runs once the server has it. */
fun FolderRulesState.add(
    folder: MailFolder,
    rule: FolderRule,
    onSaved: suspend () -> Unit = {},
) = submit(folder.accountId, onSaved) { it.withAdded(folder.id, rule) }

/** Replaces [folder]'s rule at [index] with [rule], as after editing its chip. */
fun FolderRulesState.replace(
    folder: MailFolder,
    index: Int,
    rule: FolderRule,
    onSaved: suspend () -> Unit = {},
) = submit(folder.accountId, onSaved) { it.withReplaced(folder.id, index, rule) }

/**
 * Deletes the rules of [folder] once the server has deleted it, since they would file into a
 * folder that is gone. A save the state refuses, such as over rules edited in another app, leaves
 * them, and they file into the inbox. A mailbox shared with the login has none.
 */
fun FolderRulesState.forget(folder: MailFolder) {
    if (folder.sharedIn != null || rules(folder).isEmpty()) return
    submit(folder.accountId, onFailed = {}) { it - folder.id }
}

fun FolderRulesState.remove(
    folder: MailFolder,
    index: Int,
    onSaved: suspend () -> Unit = {},
) = submit(folder.accountId, onSaved) { it.withRemoved(folder.id, index) }

/**
 * What the Add rule sheet does for [folder]: it adds a rule, or changes and deletes the rule at
 * [editing]. A saved rule then moves the matching Inbox and Archive mail when the user asked, and
 * [onMoved] runs after that move. [onClose] runs as the sheet closes.
 */
fun FolderRulesState.sheetActions(
    folder: MailFolder,
    editing: Int?,
    onClose: () -> Unit,
    onMoved: () -> Unit = {},
): RuleSheetActions =
    RuleSheetActions(
        existingCount = { existingCount(folder, it) },
        onSave = { rule, move ->
            val then: suspend () -> Unit = { if (move) moveExisting(folder, rule) { onMoved() } }
            if (editing == null) add(folder, rule, then) else replace(folder, editing, rule, then)
            onClose()
        },
        onDismiss = onClose,
        onDelete =
            editing?.let { index ->
                {
                    remove(folder, index)
                    onClose()
                }
            },
    )
