package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.shared.sidebar.components.SidebarState
import com.otus.mail.core.mailbox.SharedFolder
import com.otus.mail.core.messages.domain.inScope
import com.otus.mail.core.messages.presentation.scopeHeader
import com.otus.mail.feature.folders.presentation.FolderColorsState
import com.otus.mail.feature.folders.presentation.components.FolderColorSheet
import com.otus.mail.feature.sidebar.presentation.SidebarCounter
import com.otus.mail.feature.sidebar.presentation.components.MailSidebar
import com.otus.mail.feature.sidebar.presentation.components.SidebarContent

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
    val layout = states.behavior.behavior.sidebar
    val actions = rememberFolderActions(states.folders.colors)
    var picking by rememberSaveable { mutableStateOf(false) }
    MailSidebar(
        state,
        current.pin,
        SidebarContent(
            states.folders.all.accounts
                .inScope(scope),
            layout,
            states.behavior.behavior.inbox,
            scopeHeader(scope, states.accountAddresses()),
            SidebarCounter(scope.accountId, layout.counts),
            states.folders.colors::groupColorOf,
            actions::open,
        ),
        footer,
        onPickScope = { picking = true },
        onSettings = { onSelect(MailPage.Settings) },
        onSelect = { onSelect(launchPage(it)) },
    )
    if (picking) SidebarScopeSheet(states, scope, states.chooseScope, onDismiss = { picking = false })
    actions.Sheets()
}

@Composable
private fun rememberFolderActions(colors: FolderColorsState): SidebarFolderActions =
    remember(colors) { SidebarFolderActions(colors) }

/** The sheet that colors a long-pressed folder. Only the user's own folders have colors. */
private class SidebarFolderActions(
    private val colors: FolderColorsState,
) {
    private var coloring by mutableStateOf<SharedFolder?>(null)

    fun open(folder: SharedFolder) {
        if (folder.folders.any(colors::colorable)) coloring = folder
    }

    @Composable
    fun Sheets() {
        coloring?.let { folder ->
            FolderColorSheet(
                current = colors.colors.of(folder),
                onPick = { colors.setColor(folder.folders, it) },
                onDismiss = { coloring = null },
            )
        }
    }
}
