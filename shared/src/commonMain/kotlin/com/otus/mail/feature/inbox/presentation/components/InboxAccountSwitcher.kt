package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.otus.designsystem.shared.pages.components.belowControlPanel
import com.otus.designsystem.shared.panels.components.AccountStack
import com.otus.designsystem.shared.panels.components.PanelSheet
import com.otus.designsystem.shared.panels.components.PanelSheetHeader
import com.otus.designsystem.shared.panels.elements.PanelNavigation
import com.otus.designsystem.shared.panels.elements.PanelTitle
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.designsystem.theme.tokens.selection.FilterTokens

/**
 * The account switcher, unfolded from the inbox's control panel [collapsedHeight] tall: All mail,
 * the pinned addresses, and each account with its addresses. A choice applies at once and the
 * panel stays open, as in the sidebar's "Show mail from"; back, the chevron, the stack, a tap
 * outside, or a swipe up closes it.
 */
@Composable
internal fun InboxAccountSwitcher(
    scope: MailScopeSwitch,
    pageTitle: String,
    open: Boolean,
    collapsedHeight: Dp,
    onDismiss: () -> Unit,
) {
    val header = scope.header
    PanelSheet(
        open,
        collapsedHeight,
        onDismiss,
        header = {
            PanelSheetHeader(
                navigation = { PanelNavigation("Close account switcher", onDismiss) },
                modifier = Modifier.semantics { paneTitle = "Show mail from" },
                actions = { AccountStack(header.avatars, "Close account switcher", onDismiss) },
            ) {
                Column(Modifier.padding(start = PanelTokens.titleStart)) {
                    PanelTitle(pageTitle)
                    Text(header.title, color = OtusColors.text2, fontSize = PanelTokens.countSize)
                }
            }
        },
        Modifier
            .belowControlPanel()
            .padding(bottom = FilterTokens.screenInset),
    ) {
        Column(
            Modifier
                .weight(
                    1f,
                    fill = false,
                ).verticalScroll(rememberScrollState())
                .padding(horizontal = SheetTokens.inset),
            verticalArrangement = Arrangement.spacedBy(PanelTokens.iconGap),
        ) { MailScopeChoices(scope) }
    }
}
