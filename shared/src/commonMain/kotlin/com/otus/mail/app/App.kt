package com.otus.mail.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.mail.designsystem.components.SidebarSwipeArea
import com.otus.mail.designsystem.components.rememberSidebarState
import com.otus.mail.designsystem.theme.OtusTheme
import com.otus.mail.feature.accounts.presentation.AccountsState
import com.otus.mail.feature.accounts.presentation.screens.AccountSetupScreen
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.InboxStatus
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import com.otus.mail.feature.settings.presentation.DateFormatSettingsState
import com.otus.mail.feature.settings.presentation.screens.AppearanceSettingsScreen
import com.otus.mail.feature.settings.presentation.screens.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun App(
    dateFormatState: DateFormatSettingsState = remember { DateFormatSettingsState() },
    services: MailServices = remember { MailServices() },
) {
    val dateFormatter = rememberDateFormatter(dateFormatState)
    var page by rememberSaveable { mutableStateOf(MailPage.Inbox) }
    val sidebar = rememberSidebarState()
    val scope = rememberCoroutineScope()
    val feed = remember(services) { InboxFeedState(services.mail, scope, services.avatars) }
    val accounts = remember(services) { AccountsState(services.accounts, scope, onChange = feed::refresh) }
    // Reload whenever the inbox is shown, including at launch.
    LaunchedEffect(page == MailPage.Inbox) { if (page == MailPage.Inbox) feed.refresh() }
    // Each page keeps its own saved state, such as an inbox search, while another page is shown.
    val pages = rememberSaveableStateHolder()
    val openNavigation: () -> Unit = { scope.launch { sidebar.open() } }
    val back = { page = page.parent ?: page }
    OtusTheme {
        Box(Modifier.fillMaxSize()) {
            // Subpages lead back to their parent, so only top-level pages swipe the sidebar open.
            SidebarSwipeArea(sidebar, enabled = page.parent == null) {
                pages.SaveableStateProvider(page.name) {
                    when (page) {
                        MailPage.Inbox -> {
                            InboxScreen(
                                feed.messages,
                                dateFormatter,
                                openNavigation,
                                InboxStatus(accounts.accounts.isNotEmpty(), feed.loading, feed.notice),
                                onRefresh = feed::refresh,
                            )
                        }

                        MailPage.Settings -> {
                            SettingsScreen(
                                dateFormatState,
                                accountsSummary = accountsSummary(accounts.accounts.size),
                                onOpenNavigation = openNavigation,
                                onOpenAccounts = { page = MailPage.EmailAccounts },
                                onOpenAppearance = { page = MailPage.Appearance },
                            )
                        }

                        MailPage.EmailAccounts -> {
                            AccountSetupScreen(accounts, onBack = back)
                        }

                        MailPage.Appearance -> {
                            AppearanceSettingsScreen(dateFormatState, onBack = back)
                        }
                    }
                }
            }
            MailSidebar(
                sidebar,
                current = page.destination,
                onSelect = {
                    page = MailPage.of(it)
                    scope.launch { sidebar.close() }
                },
            )
        }
    }
}

private fun accountsSummary(count: Int): String =
    when (count) {
        0 -> "Connect a JMAP server"
        1 -> "1 account connected"
        else -> "$count accounts connected"
    }
