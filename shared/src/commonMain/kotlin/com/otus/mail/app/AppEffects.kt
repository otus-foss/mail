package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.otus.mail.core.settings.domain.SidebarPin
import com.otus.mail.core.settings.presentation.MailBehaviorState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Everything the app loads and follows while it runs, for the page [current] shows, which [show] changes. */
@Composable
internal fun AppEffects(
    services: MailServices,
    states: MailStates,
    behaviorState: MailBehaviorState,
    start: SidebarPin,
    navigation: MailNavigation,
    current: () -> MailPage,
    show: (MailPage) -> Unit,
) {
    LoadEffects(states, behaviorState, start, current, show)
    val inbox = current() == MailPage.Inbox
    // Reload whenever the inbox is shown, including at launch.
    LaunchedEffect(inbox) { if (inbox) states.list.feed.refresh() }
    ServerChangeEffects(services, states) { current() == MailPage.Inbox }
    AvatarEffects(states, behaviorState)
    CategoryEffects(states, behaviorState)
    PageRequestEffects(services, states, show)
    MessageOpenEffects(states, navigation::go)
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
    // Accounts load after launch, so filing scheduled mail waits for them.
    LaunchedEffect(states.accounts.accounts.map { it.id }) { states.compose.fileScheduled() }
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
            states.compose.fileScheduled()
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
        snapshotFlow { behaviorState.avatarOptions() }
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

/**
 * Turning categories on sorts the mail already listed, which only a new list read does, so the
 * Inbox loads again. Turning them off needs nothing: the pills go and rows keep their order.
 */
@Composable
private fun CategoryEffects(
    states: MailStates,
    behaviorState: MailBehaviorState,
) {
    LaunchedEffect(states) {
        snapshotFlow { behaviorState.behavior.list.categories.enabled }
            .drop(1)
            .collect { on -> if (on) states.list.feed.refresh() }
    }
}
