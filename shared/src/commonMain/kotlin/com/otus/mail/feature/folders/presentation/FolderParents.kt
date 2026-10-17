package com.otus.mail.feature.folders.presentation

import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.ancestors
import com.otus.mail.feature.folders.domain.folderNameProblem
import com.otus.mail.feature.folders.domain.moveTargets

/**
 * Where [folder] can move: the top level (null) and the user's own folders that take it, within
 * the depth limit and without a sibling of the same name. System folders and Read later are left
 * out, because Settings › Folders & rules lists only the user's own folders. Its current parent stays.
 */
fun AccountFolders.parentChoices(folder: MailFolder): List<MailFolder?> =
    moveTargets(this, folder).filter { parent ->
        parent?.id == folder.parentId ||
            (isOwn(parent) && folderNameProblem(folder.name, parent?.id, this, renaming = folder) == null)
    }

private fun AccountFolders.isOwn(folder: MailFolder?): Boolean =
    folder == null || (folder.isCustom && folder.id != readLaterId)

/** [folder]'s names from the top level down, such as "Clients / Acme". */
fun AccountFolders.folderPath(folder: MailFolder): String =
    (ancestors(folders, folder).reversed() + folder).joinToString(" / ") { it.name }

/** Whether [folder] has somewhere to move: the server lets it change parent and another place takes it. */
fun AccountFolders.canMove(folder: MailFolder): Boolean =
    folder.rights.mayRename && parentChoices(folder).any { it?.id != folder.parentId }
