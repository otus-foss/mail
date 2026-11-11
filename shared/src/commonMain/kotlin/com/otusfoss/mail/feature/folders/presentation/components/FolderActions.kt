package com.otusfoss.mail.feature.folders.presentation.components

import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.feature.folders.domain.FolderRule
import com.otusfoss.mail.feature.folders.domain.RuleAvailability
import com.otusfoss.mail.feature.folders.domain.withAdded
import com.otusfoss.mail.feature.folders.presentation.FolderColorsState
import com.otusfoss.mail.feature.folders.presentation.FolderRulesState
import com.otusfoss.mail.feature.folders.presentation.FoldersState
import com.otusfoss.mail.feature.folders.presentation.KnownAddress
import com.otusfoss.mail.feature.folders.presentation.account

/**
 * Everything the folder popups can ask for. Folder changes go to the server. [color] offers a
 * folder's color, kept on this device, or null for a folder that cannot have one. [ruleAddresses]
 * gives the addresses an account's new rules can suggest, or null when its server keeps no rules.
 * [newFolderColor] is the color name a new folder starts with, or null for the default.
 */
class FolderActions(
    val onCreate: (accountId: String, name: String, parentId: String?, setup: NewFolderSetup) -> Unit,
    val onRename: (MailFolder, String) -> Unit,
    val onDelete: (MailFolder) -> Unit,
    /** Moves the folder under the parent with this id, or to the top level for null. */
    val onMove: (MailFolder, parentId: String?) -> Unit,
    val color: (MailFolder) -> FolderColorChoice?,
    val ruleAddresses: (accountId: String) -> List<KnownAddress>?,
    val newFolderColor: () -> String?,
)

/** What a new folder starts with besides its name: its icon [color] and the [rules] that sort mail into it. */
data class NewFolderSetup(
    val color: String? = null,
    val rules: List<FolderRule> = emptyList(),
)

/**
 * A folder's stored color name, [current], shown even while the Folder colors switch is off, and
 * [onPick], which saves a new one or null for the default.
 */
class FolderColorChoice(
    val current: String?,
    val onPick: (String?) -> Unit,
)

/**
 * Creating, renaming, deleting, and moving go to the server; the icon color stays on this device.
 * A new folder gets its color and rules once the server has made it. [known] gives an account's
 * addresses for rule suggestions.
 */
fun folderActions(
    folders: FoldersState,
    colors: FolderColorsState,
    rules: FolderRulesState,
    known: (accountId: String) -> List<KnownAddress>,
) = FolderActions(
    onCreate = { accountId, name, parentId, setup ->
        folders.create(accountId, name, parentId) { id ->
            val folder = folders.account(accountId)?.folder(id)
            if (folder != null && setup.color != null) colors.setColor(listOf(folder), setup.color)
            if (setup.rules.isNotEmpty()) {
                rules.submit(accountId) { saved -> setup.rules.fold(saved) { all, rule -> all.withAdded(id, rule) } }
            }
        }
    },
    onRename = folders::rename,
    onDelete = folders::delete,
    onMove = { folder, parentId -> folders.place(folder, parentId) },
    color = { folder ->
        FolderColorChoice(colors.colors.of(folder)) { colors.setColor(listOf(folder), it) }
            .takeIf { colors.colorable(folder) && folder.sharedIn == null }
    },
    ruleAddresses = { accountId ->
        known(accountId).takeIf { rules.account(accountId)?.availability is RuleAvailability.Available }
    },
    newFolderColor = colors::newFolderColor,
)
