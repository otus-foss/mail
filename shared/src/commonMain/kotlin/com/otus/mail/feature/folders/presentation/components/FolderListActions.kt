package com.otus.mail.feature.folders.presentation.components

import com.otus.designsystem.components.RuleChipLine
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.presentation.FolderDrop

/**
 * What the folder list shows beside each folder and what it does with it. [placeable] lists the
 * folders a drag places among under a parent; [canDrop] says whether the server would accept a
 * drop, and [onDrop] saves where a dragged folder lands.
 */
class FolderListActions(
    val colorOf: (MailFolder) -> String?,
    val rulesOf: (MailFolder) -> List<RuleChipLine>,
    val placeable: (MailFolder, parentId: String?) -> List<MailFolder>,
    val canDrop: (FolderDrop) -> Boolean,
    val onDrop: (FolderDrop) -> Unit,
    val onOpen: (MailFolder) -> Unit,
)
