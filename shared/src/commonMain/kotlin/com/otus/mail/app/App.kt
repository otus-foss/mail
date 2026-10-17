package com.otus.mail.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.SidebarSwipeArea
import com.otus.designsystem.components.SlidingPages
import com.otus.designsystem.components.ToastHost
import com.otus.designsystem.components.ToastState
import com.otus.designsystem.components.actionBarInset
import com.otus.designsystem.components.rememberSidebarState
import com.otus.designsystem.theme.ActionBarTokens
import com.otus.designsystem.theme.AppIconStyle
import com.otus.designsystem.theme.LocalMotionScale
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.domain.MailScopeChoice
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.components.SortedMoveSheet
import com.otus.mail.feature.updates.presentation.NightlyUpdateState
import com.otus.settings.presentation.AnimationSettingsState
import com.otus.settings.presentation.AppIconSettingsState
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
    animationState: AnimationSettingsState = remember { AnimationSettingsState() },
    // Null where the platform cannot switch its icon; the launch animation then uses the accent.
    appIconState: AppIconSettingsState? = null,
    nightlyBuild: Int? = null,
) {
    val dateFormatter = rememberDateFormatter(dateFormatState)
    var page by rememberSaveable(stateSaver = MailPage.Saver) { mutableStateOf<MailPage>(MailPage.Inbox) }
    val sidebar = rememberSidebarState()
    // Whose mail the pages show; it lasts for the process, like the page.
    var chosenScope by rememberSaveable(stateSaver = MailScopeChoiceSaver) {
        mutableStateOf<MailScopeChoice>(MailScopeChoice.All)
    }
    val scope = rememberCoroutineScope()
    val states = remember(services, behaviorState) { MailStates(services, scope, behaviorState) { chosenScope } }
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
    val settings =
        remember(dateFormatState, themeState, animationState, appIconState) {
            SharedSettings(dateFormatState, themeState, animationState, appIconState)
        }
    val navigation = remember { MailNavigation(go = { page = it }, openSidebar = { scope.launch { sidebar.open() } }) }
    OtusTheme(themeState.palette) {
        CompositionLocalProvider(LocalMotionScale provides animationState.speed.scale) {
            Box(Modifier.fillMaxSize()) {
                // Subpages lead back to their parent, so only top-level pages swipe the sidebar open.
                SidebarSwipeArea(sidebar, enabled = page.parent == null) {
                    SlidingPages(page, depth = MailPage::depth) { shown ->
                        pages.SaveableStateProvider(shown.toString()) {
                            MailPageContent(shown, states, dateFormatter, settings, navigation)
                        }
                    }
                }
                AboveActionBar(states.moveToasts.toasts)
                MailSidebar(
                    sidebar,
                    current = page.root,
                    states,
                    states.mailScope,
                    update = nightly?.available,
                    onChooseScope = { chosenScope = it },
                    onSelect = {
                        page = it
                        scope.launch { sidebar.close() }
                    },
                )
                LaunchCover(states.feed, appIconState?.style ?: AppIconStyle.Accent)
            }
            // Outlives the page a move closed, such as the open message.
            states.sorting.sorted?.let { SortedMoveSheet(it, onDismiss = states.sorting::dismiss) }
            SenderRuleSheets(states)
        }
    }
}

/** Shows [toasts] above where every page keeps its action bar, at the bar's inset from the edges. */
@Composable
private fun BoxScope.AboveActionBar(toasts: ToastState) {
    ToastHost(
        toasts,
        Modifier
            .align(Alignment.BottomCenter)
            .actionBarInset()
            .padding(bottom = ActionBarTokens.height + ActionBarTokens.barInset),
    )
}
