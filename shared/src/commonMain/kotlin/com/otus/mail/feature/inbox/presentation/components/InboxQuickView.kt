package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.otus.designsystem.mail.quickview.components.QuickJump
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.pages.components.belowControlPanel
import com.otus.designsystem.shared.panels.components.AccountStack
import com.otus.designsystem.shared.panels.components.PanelSearchPill
import com.otus.designsystem.shared.panels.components.PanelSheet
import com.otus.designsystem.shared.panels.components.PanelSheetHeader
import com.otus.designsystem.shared.panels.elements.PanelNavigation
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
 * swipe up closes it. [messages] are the page's loaded mail, and [onOpenMessage] opens one. Its
 * top row is the control panel's own, chevron, search pill, and account stack in the same places,
 * so only what grows below it moves; the pill starts a search through [onSearch].
 */
@Composable
internal fun InboxQuickView(
    overlays: InboxOverlays,
    page: InboxContext,
    messages: List<MailMessage>,
    collapsedHeight: Dp,
    onOpenMessage: (MailMessage) -> Unit,
    onSearch: () -> Unit,
) {
    val quick = page.quick ?: return
    val close = { overlays.quickOpen = false }
    PanelSheet(
        overlays.quickOpen,
        collapsedHeight,
        close,
        header = {
            QuickViewHeader(
                page,
                close,
                onSearch = {
                    close()
                    onSearch()
                },
            ) {
                close()
                overlays.accountsOpen = true
            }
        },
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
    onSearch: () -> Unit,
    onAccounts: () -> Unit,
) {
    PanelSheetHeader(
        navigation = { PanelNavigation("Close quick view", onClose) },
        modifier = Modifier.semantics { paneTitle = "Quick view" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelSearchPill(page.searchPrompt, onSearch, Modifier.weight(1f))
            page.scope?.let { scope ->
                val header = scope.header
                AccountStack(header.avatars, "Showing mail from ${header.title}. Change", onAccounts)
            }
        }
    }
}
