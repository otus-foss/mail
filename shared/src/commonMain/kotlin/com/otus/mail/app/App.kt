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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.SidebarSwipeArea
import com.otus.designsystem.components.rememberSidebarState
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.components.SortedMoveSheet
import com.otus.mail.feature.updates.presentation.NightlyUpdateState
import com.otus.settings.presentation.DateFormatSettingsState
import com.otus.settings.presentation.ThemeSettingsState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Composable
fun App(
    dateFormatState: DateFormatSettingsState = remember { DateFormatSettingsState() },
    services: MailServices = remember { MailServices() },
    behaviorState: MailBehaviorState = remember { MailBehaviorState() },
    themeState: ThemeSettingsState = remember { ThemeSettingsState() },
    nightlyBuild: Int? = null,
) {
    val dateFormatter = rememberDateFormatter(dateFormatState)
    var page by rememberSaveable(stateSaver = MailPage.Saver) { mutableStateOf<MailPage>(MailPage.Inbox) }
    val sidebar = rememberSidebarState()
    // The account whose counts the sidebar shows; null shows every account's.
    var sidebarAccount by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val states = remember(services, behaviorState) { MailStates(services, scope, behaviorState) }
    // Only nightly builds look for a newer nightly, once per launch.
    val nightly = remember(services, nightlyBuild) { nightlyBuild?.let { NightlyUpdateState(services.nightly, it) } }
    LaunchedEffect(nightly) { nightly?.check() }
    // Rules decide whether an open message offers to start one.
    LaunchedEffect(states) { states.rules.refresh() }
    // Folders fill the sidebar. Turning Read later on finds or creates its folders; off unmarks them.
    LaunchedEffect(states, behaviorState.behavior.readLater) { states.folders.refresh() }
    // Reload whenever the inbox is shown, including at launch.
    LaunchedEffect(page == MailPage.Inbox) { if (page == MailPage.Inbox) states.feed.refresh() }
    // Changing Website icons stops the inbox's avatar lookups at once and looks again with the new choice.
    LaunchedEffect(states) {
        snapshotFlow { behaviorState.behavior.websiteIcons }.drop(1).collect { states.feed.refresh() }
    }
    // Each page keeps its own saved state, such as an inbox search, while another page is shown.
    val pages = rememberSaveableStateHolder()
    val navigation = remember { MailNavigation(go = { page = it }, openSidebar = { scope.launch { sidebar.open() } }) }
    OtusTheme(themeState.palette) {
        Box(Modifier.fillMaxSize()) {
            // Subpages lead back to their parent, so only top-level pages swipe the sidebar open.
            SidebarSwipeArea(sidebar, enabled = page.parent == null) {
                pages.SaveableStateProvider(page.toString()) {
                    MailPageContent(page, states, dateFormatter, dateFormatState, themeState, navigation)
                }
            }
            MailSidebar(
                sidebar,
                current = page.root,
                states,
                sidebarAccount,
                update = nightly?.available,
                onSelectAccount = { sidebarAccount = it },
                onSelect = {
                    page = it
                    scope.launch { sidebar.close() }
                },
            )
        }
        // Outlives the page a move closed, such as the open message.
        states.sorting.sorted?.let { SortedMoveSheet(it, onDismiss = states.sorting::dismiss) }
    }
}
