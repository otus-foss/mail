package com.otus.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.actionbar.components.ActionBar
import com.otus.designsystem.shared.actionbar.components.BarAction
import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.settings.components.SketchMarker
import com.otus.mail.core.messages.domain.ActionBarLayout
import com.otus.mail.core.messages.domain.ActionBarPlace
import com.otus.mail.core.messages.domain.BarSlot
import com.otus.mail.core.messages.presentation.actions.buttonLabel
import com.otus.mail.core.messages.presentation.actions.symbol

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
            BarAction(UiAction(button.buttonLabel(), button.symbol) { onSlot(BarSlot.Secondary(index)) })
        }
    ActionBar(
        BarAction(
            UiAction(layout.primary.buttonLabel(primary = true), layout.primary.symbol) { onSlot(BarSlot.Primary) },
        ),
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
