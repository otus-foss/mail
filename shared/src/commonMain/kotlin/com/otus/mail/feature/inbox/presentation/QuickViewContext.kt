package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.QuickViewModule
import org.jetbrains.compose.resources.DrawableResource

/** A folder the quick view's Jump to row opens: its [title], [icon], and [onOpen]. */
class QuickJumpTarget(
    val title: String,
    val icon: DrawableResource,
    val onOpen: () -> Unit,
)

/**
 * What the inbox's quick view shows when its control panel is pulled down: the [modules] the user
 * picked, in order, then Jump to with the [targets], usually the sidebar's pins.
 */
data class QuickViewContext(
    val modules: List<QuickViewModule> = QuickViewModule.Defaults,
    val targets: List<QuickJumpTarget> = emptyList(),
)
