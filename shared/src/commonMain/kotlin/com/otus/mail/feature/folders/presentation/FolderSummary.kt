package com.otus.mail.feature.folders.presentation

import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailFolder

/** How many folders sit directly inside [folder]. */
fun AccountFolders.subfolderCount(folder: MailFolder): Int = folders.count { it.parentId == folder.id }

/** A folder page's first line, such as "45 messages · 3 unread · 2 folders inside"; zero counts are left out. */
fun MailFolder.summary(subfolders: Int): String =
    listOfNotNull(
        if (total == 0L) "No messages" else counted(total, "message"),
        "$unread unread".takeIf { unread > 0 },
        "${counted(subfolders.toLong(), "folder")} inside".takeIf { subfolders > 0 },
    ).joinToString(" · ")

/** Only an empty folder can be deleted; servers refuse the others (RFC 8621, section 2.5). */
fun MailFolder.deletable(subfolders: Int): Boolean = isCustom && rights.mayDelete && total == 0L && subfolders == 0

private fun counted(
    count: Long,
    noun: String,
): String = if (count == 1L) "1 $noun" else "$count ${noun}s"
