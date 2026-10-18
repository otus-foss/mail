package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.ActionBar
import com.otus.designsystem.components.BarAction
import com.otus.designsystem.components.SketchMarker
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.BarSlot

/**
 * [layout] as its bar shows it; each button runs [onSlot] with its slot instead of its action.
 * With [addSlot], a bar with room ends its secondary group in a dashed add slot for the next one.
 */
@Composable
internal fun ActionBarPreview(
    layout: ActionBarLayout,
    onSlot: (BarSlot) -> Unit = {},
    addSlot: Boolean = false,
) {
    val secondary =
        layout.secondary.mapIndexed { index, button ->
            BarAction(button.buttonLabel(), button.symbol) { onSlot(BarSlot.Secondary(index)) }
        }
    ActionBar(
        BarAction(layout.primary.buttonLabel(primary = true), layout.primary.symbol) { onSlot(BarSlot.Primary) },
        secondary = secondary,
        onAdd = { onSlot(BarSlot.Secondary(layout.secondary.size)) }.takeIf { addSlot },
    )
}

internal val ActionBarPlace.title: String
    get() =
        when (this) {
            ActionBarPlace.Inbox -> "Message list"
            ActionBarPlace.Message -> "Open message"
            ActionBarPlace.Selection -> "Selected messages"
            ActionBarPlace.Compose -> "Writing"
            ActionBarPlace.Everywhere -> "Every screen"
        }

/** Where the bar shows, under the title of its settings card. */
internal val ActionBarPlace.description: String
    get() =
        when (this) {
            ActionBarPlace.Inbox -> "Inbox and folders"
            ActionBarPlace.Message -> "Reading one message"
            ActionBarPlace.Selection -> "After a long press"
            ActionBarPlace.Compose -> "New mail and replies"
            ActionBarPlace.Everywhere -> "Lists, messages, and writing"
        }

/** How the card's page sketch hints at the place. */
internal val ActionBarPlace.marker: SketchMarker
    get() =
        when (this) {
            ActionBarPlace.Inbox, ActionBarPlace.Everywhere -> SketchMarker.Row
            ActionBarPlace.Selection -> SketchMarker.Selected
            ActionBarPlace.Message, ActionBarPlace.Compose -> SketchMarker.None
        }
