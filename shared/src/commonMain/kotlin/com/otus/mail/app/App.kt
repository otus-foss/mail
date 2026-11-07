package com.otus.mail.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.designsystem.shared.feedback.components.ToastHost
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.designsystem.shared.pages.components.BottomClearance
import com.otus.designsystem.shared.pages.components.LocalBottomClearance
import com.otus.designsystem.shared.pages.components.SlidingPages
import com.otus.designsystem.shared.sidebar.components.SidebarSwipeArea
import com.otus.designsystem.shared.sidebar.components.rememberSidebarState
import com.otus.designsystem.theme.OtusTheme
import com.otus.designsystem.theme.brand.AppIconStyle
import com.otus.designsystem.theme.motion.LocalMotionScale
import com.otus.mail.core.messages.domain.MailScopeChoice
import com.otus.mail.core.messages.presentation.components.SortedMoveSheet
import com.otus.mail.core.settings.domain.SidebarPin
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.feature.folders.presentation.components.SenderRuleSheets
import com.otus.mail.feature.inbox.presentation.components.LaunchCover
import com.otus.mail.feature.updates.domain.InstalledBuild
import com.otus.mail.feature.updates.domain.ReleaseChannel
import com.otus.mail.feature.updates.presentation.NightlyUpdateState
import com.otus.mail.feature.updates.presentation.components.BuildFooter
import com.otus.mail.feature.updates.presentation.components.UpdateSheets
import com.otus.mail.feature.updates.presentation.components.UpdateToastEffect
import com.otus.settings.presentation.AnimationSettingsState
import com.otus.settings.presentation.AppIconSettingsState
import com.otus.settings.presentation.DateFormatSettingsState
import com.otus.settings.presentation.ThemeSettingsState
import com.otus.settings.presentation.components.LocalDateFormatter
import com.otus.settings.presentation.components.rememberDateFormatter
import com.otus.storage.inMemoryChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Composable
fun App(
    dateFormatState: DateFormatSettingsState = remember { DateFormatSettingsState(inMemoryChoice()) },
    services: MailServices = remember { MailServices() },
    behaviorState: MailBehaviorState = remember { MailBehaviorState() },
    themeState: ThemeSettingsState = remember { ThemeSettingsState(inMemoryChoice()) },
    animationState: AnimationSettingsState = remember { AnimationSettingsState(inMemoryChoice()) },
    // Null where the platform cannot switch its icon; the launch animation then uses the accent.
    appIconState: AppIconSettingsState? = null,
    build: InstalledBuild? = null,
) {
    val dateFormatter = rememberDateFormatter(dateFormatState)
    val currentDates = rememberUpdatedState(dateFormatter)
    // The start page is read once per launch; changing it in settings applies at the next launch.
    val start = remember(behaviorState) { behaviorState.behavior.sidebar.startPage }
    var page by rememberSaveable(stateSaver = MailPageSaver) { mutableStateOf(launchPage(start)) }
    val sidebar = rememberSidebarState()
    // Whose mail the pages show; it lasts for the process, like the page.
    var chosenScope by rememberSaveable(stateSaver = MailScopeChoiceSaver) {
        mutableStateOf<MailScopeChoice>(MailScopeChoice.All)
    }
    val scope = rememberCoroutineScope()
    val states =
        remember(services, behaviorState) {
            MailStates(services, scope, behaviorState, { chosenScope }, { chosenScope = it }) { currentDates.value }
        }
    val nightly = rememberNightlyUpdate(services, build)
    var updateOpen by rememberSaveable { mutableStateOf(false) }
    LoadEffects(states, behaviorState, start, current = { page }) { page = it }
    // Reload whenever the inbox is shown, including at launch.
    LaunchedEffect(page == MailPage.Inbox) { if (page == MailPage.Inbox) states.list.feed.refresh() }
    ServerChangeEffects(services, states) { page == MailPage.Inbox }
    AvatarEffects(states, behaviorState)
    // Each page keeps its own saved state, such as an inbox search, while another page is shown.
    val pages = rememberSaveableStateHolder()
    val settings = rememberSharedSettings(dateFormatState, themeState, animationState, appIconState)
    val navigation = rememberMailNavigation({ page = it }) { scope.launch { sidebar.open() } }
    PageRequestEffects(services, states) { page = it }
    MessageOpenEffects(states, navigation::go)
    OtusTheme(themeState.palette) {
        CompositionLocalProvider(
            LocalMotionScale provides animationState.speed.scale,
            LocalDateFormatter provides dateFormatter,
            LocalBottomClearance provides remember { BottomClearance() },
        ) {
            Box(Modifier.fillMaxSize()) {
                // Back from another sidebar page returns to the inbox, so only the inbox's back closes
                // Mail. Pages and sheets shown later take back first.
                NavigationBackHandler(
                    rememberNavigationEventState(NavigationEventInfo.None),
                    isBackEnabled = page.parent == null && page != MailPage.Inbox,
                    onBackCompleted = { page = MailPage.Inbox },
                )
                // Subpages lead back to their parent, so only top-level pages swipe the sidebar open.
                SidebarSwipeArea(sidebar, enabled = page.parent == null) {
                    SlidingPages(page, depth = MailPage::depth) { shown ->
                        pages.SaveableStateProvider(shown.toString()) {
                            MailPageContent(shown, states, dateFormatter, settings, navigation)
                        }
                    }
                }
                BottomToasts(states.moveToasts.toasts)
                UpdateToastEffect(nightly, behaviorState.behavior.updateToasts, states.moveToasts.toasts) {
                    updateOpen = true
                }
                AppSidebar(
                    sidebar,
                    current = page.root,
                    states,
                    footer = { build?.let { BuildFooter(it, nightly) { updateOpen = true } } },
                    onSelect = {
                        navigation.go(it)
                        scope.launch { sidebar.close() }
                    },
                )
                LaunchCover(
                    states.list.feed,
                    appIconState?.style ?: AppIconStyle.Accent,
                    inbox = start == SidebarPin.Inbox,
                )
            }
            // Outlives the page a move closed, such as the open message.
            states.folders.sorting.sorted
                ?.let { SortedMoveSheet(it, onDismiss = states.folders.sorting::dismiss) }
            SenderRuleSheets(
                states.folders.senderRules,
                states.folders.all,
                states.folders.rules,
                known = states::knownAddresses,
            ) {
                states.folders.all.refresh()
                states.list.refreshAll()
            }
            if (updateOpen && nightly != null) {
                InstallingUpdateSheets(nightly, states.moveToasts.toasts, scope) { updateOpen = false }
            }
        }
    }
}

/**
 * Loads the rules and the folders at launch, and the folders again whenever Read later turns on or
 * off. Once the folders have loaded, a [start] page no account has gives way to the Inbox through
 * [show] while [current] still shows it.
 */
@Composable
private fun LoadEffects(
    states: MailStates,
    behaviorState: MailBehaviorState,
    start: SidebarPin,
    current: () -> MailPage,
    show: (MailPage) -> Unit,
) {
    // Rules decide whether an open message offers to start one.
    LaunchedEffect(states) { states.folders.rules.refresh() }
    // Folders fill the sidebar. Turning Read later on finds or creates its folders; off unmarks them.
    LaunchedEffect(states, behaviorState.behavior.readLater) { states.folders.all.refresh() }
    StartPageFallback(start, states.folders.all, current, show)
}

/**
 * Opens with [show] the pages toasts ask for, including ones shown before the activity was
 * recreated. Messages an earlier run could not hand to the server go now, or wait in a toast.
 */
@Composable
private fun PageRequestEffects(
    services: MailServices,
    states: MailStates,
    show: (MailPage) -> Unit,
) {
    LaunchedEffect(services) { services.requests.pages.collect { show(it) } }
    LaunchedEffect(states) { states.compose.outgoing.resume { states.requests.pages.show(MailPage.Compose(it)) } }
}

/**
 * Shows mail that arrives or changes elsewhere without a pull: each push reloads the folders, the
 * open list pages, and the inbox while [inboxShown]; the inbox reloads anyway when shown. While
 * Mail is off screen, pushes wait, and one reload follows when it comes back. A push often follows
 * the user's own action, so the lists keep a notice about that action's failure.
 */
@Composable
private fun ServerChangeEffects(
    services: MailServices,
    states: MailStates,
    inboxShown: () -> Boolean,
) {
    LaunchedEffect(states) {
        reloadWhileShown(services.serverChanges.changes, services.visibility.shown) {
            states.folders.all.refresh()
            states.list.refreshPages(keepReport = true)
            if (inboxShown()) states.list.feed.refresh(keepReport = true)
        }
    }
}

/** Keeps sender avatars in step with the avatar settings and the contacts' photos. */
@Composable
private fun AvatarEffects(
    states: MailStates,
    behaviorState: MailBehaviorState,
) {
    // Changing where avatars load from stops the lists' avatar lookups and looks again with the new choice.
    LaunchedEffect(states) {
        snapshotFlow { states.avatarOptions() }
            .drop(1)
            .collect { states.list.refreshAll() }
    }
    // Contacts load at launch so sender avatars can show their photos. Photos from all accounts
    // changes which of those photos the avatars take.
    LaunchedEffect(states) {
        states.contacts.cards.loadCards()
        snapshotFlow { behaviorState.behavior.avatars.allAccountPhotos }
            .drop(1)
            .collect { states.contacts.avatars.apply() }
    }
}

/** Only nightly builds on a platform that can install one look for a newer nightly, once per launch. */
@Composable
private fun rememberNightlyUpdate(
    services: MailServices,
    build: InstalledBuild?,
): NightlyUpdateState? {
    val nightly = remember(services, build) { services.nightlyState(build) }
    LaunchedEffect(nightly) { nightly?.check() }
    return nightly
}

private fun MailServices.nightlyState(build: InstalledBuild?): NightlyUpdateState? =
    build
        ?.takeIf { it.channel == ReleaseChannel.Nightly }
        ?.let { installed -> nightly.installer?.let { NightlyUpdateState(nightly.feed, installed.versionCode, it) } }

/**
 * The [UpdateSheets], which install [nightly] in [scope] and say in [toasts] when that fails. The
 * sheet closes as the download starts, so the download runs in a scope that outlives it.
 */
@Composable
private fun InstallingUpdateSheets(
    nightly: NightlyUpdateState,
    toasts: ToastState,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    UpdateSheets(
        nightly,
        onInstall = {
            scope.launch { if (!nightly.install()) toasts.show(ToastMessage("Couldn't download the update")) }
        },
        onDismiss = onDismiss,
    )
}

/** Shows [toasts] at the bottom, above the page's action bar when it has one. */
@Composable
private fun BoxScope.BottomToasts(toasts: ToastState) {
    ToastHost(toasts, Modifier.align(Alignment.BottomCenter))
}
