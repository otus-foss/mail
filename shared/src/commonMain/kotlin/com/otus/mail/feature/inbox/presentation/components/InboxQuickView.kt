package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.otus.designsystem.mail.quickview.components.QuickJump
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.pages.components.belowControlPanel
import com.otus.designsystem.shared.panels.components.AccountStack
import com.otus.designsystem.shared.panels.components.PanelSheet
import com.otus.designsystem.shared.panels.components.PanelSheetHeader
import com.otus.designsystem.shared.panels.elements.PanelNavigation
import com.otus.designsystem.shared.panels.elements.PanelTitle
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.designsystem.theme.tokens.layout.QuickViewTokens
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.designsystem.theme.tokens.selection.FilterTokens
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.QuickViewModule
import com.otus.mail.feature.inbox.presentation.InboxContext

/**
 * The inbox's quick view, the first pull on its control panel, grown out of the panel
 * [collapsedHeight] tall: the modules the page's [InboxContext.quick] picks, then Jump to. Pulling
 * it down again, or its filter button, opens every filter; back, the chevron, a tap outside, or a
 * swipe up closes it. [messages] are the page's loaded mail, and [onOpenMessage] opens one.
 */
@Composable
internal fun InboxQuickView(
    overlays: InboxOverlays,
    page: InboxContext,
    messages: List<MailMessage>,
    collapsedHeight: Dp,
    onOpenMessage: (MailMessage) -> Unit,
) {
    val quick = page.quick ?: return
    val close = { overlays.quickOpen = false }
    PanelSheet(
        overlays.quickOpen,
        collapsedHeight,
        close,
        header = { QuickViewHeader(page, close) { overlays.accountsOpen = true } },
        Modifier
            .belowControlPanel()
            .padding(bottom = FilterTokens.screenInset),
        onPullDown = overlays::allFilters,
    ) {
        Column(
            Modifier.padding(horizontal = SheetTokens.inset),
            verticalArrangement = Arrangement.spacedBy(QuickViewTokens.moduleGap),
        ) {
            val sources = QuickSources(overlays, page, messages, onOpenMessage)
            quick.modules.forEach { module ->
                when (module) {
                    QuickViewModule.Counts -> QuickCountsModule(sources)
                    QuickViewModule.People -> QuickPeopleModule(sources)
                    QuickViewModule.Addresses -> QuickAddressesModule(sources)
                    QuickViewModule.RecentFiles -> QuickFilesModule(sources)
                }
            }
            QuickJump(overlays::allFilters) {
                quick.targets.forEach { target ->
                    Chip(target.title, {
                        close()
                        target.onOpen()
                    }) { Symbol(target.icon) }
                }
            }
        }
    }
}

@Composable
private fun QuickViewHeader(
    page: InboxContext,
    onClose: () -> Unit,
    onAccounts: () -> Unit,
) {
    val scope = page.scope
    PanelSheetHeader(
        navigation = { PanelNavigation("Close quick view", onClose) },
        modifier = Modifier.semantics { paneTitle = "Quick view" },
        actions =
            scope?.let {
                {
                    AccountStack(
                        it.header.avatars,
                        "Show mail from",
                        onClick = {
                            onClose()
                            onAccounts()
                        },
                    )
                }
            },
    ) {
        Column(Modifier.padding(start = PanelTokens.titleStart)) {
            PanelTitle(page.title)
            scope?.let { Text(it.header.title, color = OtusColors.text2, fontSize = PanelTokens.countSize) }
        }
    }
}
