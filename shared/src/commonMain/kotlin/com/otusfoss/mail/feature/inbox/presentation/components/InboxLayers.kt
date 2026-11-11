package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.otusfoss.mail.feature.inbox.presentation.InboxContext

/** The account switcher over [page], grown out of the control panel's [panelHeight]. */
@Composable
internal fun InboxLayers(
    page: InboxContext,
    panelHeight: Dp,
    overlays: InboxOverlays,
) {
    page.scope?.let {
        InboxAccountSwitcher(it, page.title, overlays.accountsOpen, panelHeight) { overlays.accountsOpen = false }
    }
}
