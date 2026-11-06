package com.otus.mail.core.mailbox.presentation

import com.otus.designsystem.mail.folders.elements.folderSymbol
import com.otus.mail.core.mailbox.FolderNode
import com.otus.mail.core.mailbox.MailboxRole
import org.jetbrains.compose.resources.DrawableResource

/** What every page, pin, and pill calls the Read later folders of all accounts. */
const val READ_LATER_TITLE = "Read later"

/** The role's symbol; no role has the plain folder's. */
val MailboxRole?.symbol: DrawableResource
    get() = folderSymbol(this?.wire)

/** System folders and Read later have their own symbols; the user's other folders share one. */
val FolderNode.symbol: DrawableResource
    get() = folderSymbol(folder.role?.wire, readLater)
