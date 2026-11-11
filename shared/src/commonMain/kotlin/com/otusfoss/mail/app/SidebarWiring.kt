package com.otusfoss.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otusfoss.core.designsystem.shared.sidebar.components.SidebarState
import com.otusfoss.mail.core.messages.domain.inScope
import com.otusfoss.mail.core.messages.presentation.scopeHeader
import com.otusfoss.mail.feature.folders.presentation.components.SharedFolderColoring
import com.otusfoss.mail.feature.sidebar.presentation.SidebarCounter
import com.otusfoss.mail.feature.sidebar.presentation.components.MailSidebar
import com.otusfoss.mail.feature.sidebar.presentation.components.SidebarContent

/**
 * The sidebar for [current]'s sidebar destination, with the "Show mail from" sheet and the color
 * sheet of a long-pressed folder; choosing a mailbox runs [onSelect] with its page.
 */
@Composable
internal fun AppSidebar(
    state: SidebarState,
    current: MailPage,
    states: MailStates,
    footer: @Composable () -> Unit,
    onSelect: (MailPage) -> Unit,
) {
    val scope = states.mailScope
    val layout = states.behavior.behavior.sidebar.layout
    val coloring = remember(states.folders.colors) { SharedFolderColoring(states.folders.colors) }
    var picking by rememberSaveable { mutableStateOf(false) }
    MailSidebar(
        state,
        current.pin,
        SidebarContent(
            states.folders.all.accounts
                .inScope(scope),
            layout,
            states.behavior.behavior.list.inbox,
            scopeHeader(scope, states.accountAddresses()),
            SidebarCounter(scope.accountId, layout.counts),
            states.folders.colors::groupColorOf,
            coloring::open,
        ),
        footer,
        onPickScope = { picking = true },
        onSettings = { onSelect(MailPage.Settings) },
        onSelect = { onSelect(launchPage(it)) },
    )
    if (picking) SidebarScopeSheet(states, scope, states.chooseScope, onDismiss = { picking = false })
    coloring.Sheet()
}
