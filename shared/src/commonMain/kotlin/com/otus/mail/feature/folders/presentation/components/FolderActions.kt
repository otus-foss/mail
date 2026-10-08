package com.otus.mail.feature.folders.presentation.components

import com.otus.mail.feature.folders.domain.MailFolder

/** Everything the folder popups can ask for. Each change goes to the server. */
class FolderActions(
    val onCreate: (accountId: String, name: String, parentId: String?) -> Unit,
    val onRename: (MailFolder, String) -> Unit,
    val onMove: (MailFolder, String?) -> Unit,
    val onDelete: (MailFolder) -> Unit,
    val onOpen: (MailFolder) -> Unit,
)
