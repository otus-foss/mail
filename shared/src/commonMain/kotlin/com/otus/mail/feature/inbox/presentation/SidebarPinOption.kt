package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.SidebarPin
import org.jetbrains.compose.resources.DrawableResource

/**
 * A mailbox the accounts' folders let the user pin: its [title] and [icon], and the [group] and
 * [path] Settings lists it under. App wiring finds these, because the inbox feature does not know folders.
 */
data class SidebarPinOption(
    val pin: SidebarPin,
    val title: String,
    val icon: DrawableResource,
    val group: String,
    val path: String = title,
)
