package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.ActionBar
import com.otus.designsystem.components.BarAction
import com.otus.designsystem.components.SketchMarker
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.MailAction

/** [layout] as its bar shows it; every button runs [onClick] instead of its action. */
@Composable
internal fun ActionBarPreview(
    layout: ActionBarLayout,
    onClick: () -> Unit = {},
) {
    val preview = { action: MailAction ->
        BarAction(action.buttonLabel, action.symbol, onClick = onClick)
    }
    ActionBar(preview(layout.primary), secondary = layout.secondary.map(preview))
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
