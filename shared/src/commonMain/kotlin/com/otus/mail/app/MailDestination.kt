package com.otus.mail.app

import com.otus.mail.designsystem.resources.Res
import com.otus.mail.designsystem.resources.symbol_inbox
import com.otus.mail.designsystem.resources.symbol_settings
import org.jetbrains.compose.resources.DrawableResource

/** Pages that exist today. JMAP mailboxes join this list once accounts are connected. */
internal enum class MailDestination(
    val label: String,
    val icon: DrawableResource,
) {
    Inbox("Inbox", Res.drawable.symbol_inbox),
    Settings("Settings", Res.drawable.symbol_settings),
}
