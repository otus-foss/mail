package com.otus.mail.feature.folders.presentation

import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.ancestors
import com.otus.mail.feature.folders.domain.FolderRule

/** A folder above another whose [rules] the server checks first. */
data class ParentRules(
    val folder: MailFolder,
    val rules: List<FolderRule>,
)

/**
 * Each folder above [folder] in [folders] that has rules, top level first. The server checks the
 * rules in that order, and mail one of them matches lands in that parent instead of [folder].
 */
fun parentRules(
    folders: List<MailFolder>,
    folder: MailFolder,
    rulesOf: (MailFolder) -> List<FolderRule>,
): List<ParentRules> =
    ancestors(folders, folder)
        .reversed()
        .map { ParentRules(it, rulesOf(it)) }
        .filter { it.rules.isNotEmpty() }
