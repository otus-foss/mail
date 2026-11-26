package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.otusfoss.mail.core.messages.presentation.MessageActions
import com.otusfoss.mail.core.messages.presentation.components.MoveChoice
import com.otusfoss.mail.feature.inbox.presentation.InboxContext

/**
 * The account switcher over [page], grown out of the control panel's [panelHeight], and the Move
 * to sheet a row's Move swipe opens through [moveChoice], which needs [actions].
 */
@Composable
internal fun InboxLayers(
    page: InboxContext,
    panelHeight: Dp,
    overlays: InboxOverlays,
    moveChoice: MoveChoice,
    actions: MessageActions?,
) {
    page.scope?.let {
        InboxAccountSwitcher(it, page.title, overlays.accountsOpen, panelHeight) { overlays.accountsOpen = false }
    }
    actions?.let { moveChoice.Sheet(it) }
}
