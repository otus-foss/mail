package com.otusfoss.mail.core.mailbox.presentation

import com.otusfoss.core.designsystem.mail.folders.elements.folderSymbol
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_bookmark
import com.otusfoss.mail.core.mailbox.FolderNode
import com.otusfoss.mail.core.mailbox.MailboxRole
import org.jetbrains.compose.resources.DrawableResource

/** What every page, pin, and pill calls the Read later folders of all accounts. */
const val READ_LATER_TITLE = "Read later"

/** The role's symbol; no role has the plain folder's. */
val MailboxRole?.symbol: DrawableResource
    get() = folderSymbol(this?.wire)

/** System folders and Read later have their own symbols; the user's other folders share one. */
val FolderNode.symbol: DrawableResource
    get() = if (readLater) Res.drawable.symbol_bookmark else folderSymbol(folder.role?.wire)
