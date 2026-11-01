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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.otus.mail.feature.inbox.domain.MailScopeChoice
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.components.BackHandler
import com.otus.mail.feature.inbox.presentation.components.SortedMoveSheet
import com.otus.mail.feature.updates.domain.InstalledBuild
import com.otus.mail.feature.updates.domain.ReleaseChannel
import com.otus.mail.feature.updates.presentation.NightlyUpdateState
import com.otus.mail.feature.updates.presentation.components.BuildFooter
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
    build: InstalledBuild? = null,
) {
    val dateFormatter = rememberDateFormatter(dateFormatState)
    var page by rememberSaveable(stateSaver = MailPage.Saver) { mutableStateOf<MailPage>(MailPage.Inbox) }
    val sidebar = rememberSidebarState()
    // Whose mail the pages show; it lasts for the process, like the page.
    var chosenScope by rememberSaveable(stateSaver = MailScopeChoiceSaver) {
        mutableStateOf<MailScopeChoice>(MailScopeChoice.All)
    }
    val scope = rememberCoroutineScope()
    val states =
        remember(services, behaviorState) {
            MailStates(services, scope, behaviorState, { chosenScope }) { chosenScope = it }
        }
    val nightly = rememberNightlyUpdate(services, build)
    // Rules decide whether an open message offers to start one.
    LaunchedEffect(states) { states.rules.refresh() }
    // Folders fill the sidebar. Turning Read later on finds or creates its folders; off unmarks them.
    LaunchedEffect(states, behaviorState.behavior.readLater) { states.folders.refresh() }
    // Reload whenever the inbox is shown, including at launch.
    LaunchedEffect(page == MailPage.Inbox) { if (page == MailPage.Inbox) states.feed.refresh() }
    ServerChangeEffects(services, states) { page == MailPage.Inbox }
    AvatarEffects(states, behaviorState)
    // Each page keeps its own saved state, such as an inbox search, while another page is shown.
    val pages = rememberSaveableStateHolder()
    val settings =
        remember(dateFormatState, themeState, animationState, appIconState) {
            SharedSettings(dateFormatState, themeState, animationState, appIconState)
        }
    val navigation = rememberMailNavigation({ page = it }) { scope.launch { sidebar.open() } }
    // Messages an earlier run could not hand to the server go now, or wait in a toast for the user.
    LaunchedEffect(states) { states.outgoing.resume { navigation.go(MailPage.Compose(it)) } }
    OtusTheme(themeState.palette) {
        CompositionLocalProvider(
            LocalMotionScale provides animationState.speed.scale,
            LocalBottomClearance provides remember { BottomClearance() },
        ) {
            Box(Modifier.fillMaxSize()) {
                // Back from another sidebar page returns to the inbox, so only the inbox's back closes
                // Mail. Pages and sheets shown later take back first.
                BackHandler(enabled = page.parent == null && page != MailPage.Inbox) { page = MailPage.Inbox }
                // Subpages lead back to their parent, so only top-level pages swipe the sidebar open.
                SidebarSwipeArea(sidebar, enabled = page.parent == null) {
                    SlidingPages(page, depth = MailPage::depth) { shown ->
                        pages.SaveableStateProvider(shown.toString()) {
                            MailPageContent(shown, states, dateFormatter, settings, navigation)
                        }
                    }
                }
                BottomToasts(states.moveToasts.toasts)
                MailSidebar(
                    sidebar,
                    current = page.root,
                    states,
                    states.mailScope,
                    footer = { build?.let { UpdatingFooter(it, nightly, states.moveToasts.toasts) } },
                    onChooseScope = states.chooseScope,
                    onSelect = {
                        navigation.go(it)
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

/**
 * Shows mail that arrives or changes elsewhere without a pull: each push reloads the folders, and
 * the inbox while [inboxShown]. Other pages reload when shown. A push often follows the user's own
 * action, so the inbox keeps a notice about that action's failure.
 */
@Composable
private fun ServerChangeEffects(
    services: MailServices,
    states: MailStates,
    inboxShown: () -> Boolean,
) {
    LaunchedEffect(states) {
        services.serverChanges.changes.collect {
            states.folders.refresh()
            if (inboxShown()) states.feed.refresh(keepReport = true)
        }
    }
}

/** Keeps sender avatars in step with the avatar settings and the contacts' photos. */
@Composable
private fun AvatarEffects(
    states: MailStates,
    behaviorState: MailBehaviorState,
) {
    // Changing where avatars load from stops the inbox's avatar lookups and looks again with the new choice.
    LaunchedEffect(states) {
        snapshotFlow { states.avatarOptions() }
            .drop(1)
            .collect { states.feed.refresh() }
    }
    // Contacts load at launch so sender avatars can show their photos. Photos from all accounts
    // changes which of those photos the avatars take.
    LaunchedEffect(states) {
        states.contacts.loadCards()
        snapshotFlow { behaviorState.behavior.allAccountPhotos }
            .drop(1)
            .collect { states.contactAvatars.apply() }
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

/** The [BuildFooter], which installs [nightly] in the background and says in [toasts] when that fails. */
@Composable
private fun UpdatingFooter(
    build: InstalledBuild,
    nightly: NightlyUpdateState?,
    toasts: ToastState,
) {
    val scope = rememberCoroutineScope()
    BuildFooter(build, nightly) {
        scope.launch {
            if (nightly?.install() == false) toasts.show(ToastMessage("Couldn't download the update"))
        }
    }
}

/** Shows [toasts] at the bottom, above the page's action bar when it has one. */
@Composable
private fun BoxScope.BottomToasts(toasts: ToastState) {
    ToastHost(toasts, Modifier.align(Alignment.BottomCenter))
}
