package com.otus.mail.feature.folders.presentation

import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.ancestors
import com.otus.mail.feature.folders.domain.folderNameProblem
import com.otus.mail.feature.folders.domain.moveTargets

/**
 * Where [folder] can move: the top level (null) and the folders that take it, within the depth
 * limit and without a sibling of the same name. Its current parent stays in the list.
 */
fun AccountFolders.parentChoices(folder: MailFolder): List<MailFolder?> =
    moveTargets(this, folder).filter { parent ->
        parent?.id == folder.parentId || folderNameProblem(folder.name, parent?.id, this, renaming = folder) == null
    }

/** [folder]'s names from the top level down, such as "Clients / Acme". */
fun AccountFolders.folderPath(folder: MailFolder): String =
    (ancestors(folders, folder).reversed() + folder).joinToString(" / ") { it.name }
