package com.otus.mail.feature.inbox.presentation

import androidx.compose.ui.graphics.Color
import com.otus.mail.feature.inbox.domain.SidebarPin
import org.jetbrains.compose.resources.DrawableResource

/**
 * A mailbox the accounts' folders let the user pin: its [title], its [icon] in [tint], such as a
 * folder's color, and the [group] and [path] Settings lists it under. App wiring finds these,
 * because the inbox feature does not know folders.
 */
data class SidebarPinOption(
    val pin: SidebarPin,
    val title: String,
    val icon: DrawableResource,
    val group: String,
    val path: String = title,
    val tint: Color? = null,
)
