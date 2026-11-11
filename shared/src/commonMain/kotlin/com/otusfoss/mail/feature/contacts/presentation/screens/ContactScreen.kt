package com.otusfoss.mail.feature.contacts.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_call_merge
import com.otusfoss.core.designsystem.shared.actionbar.components.ActionBarHost
import com.otusfoss.core.designsystem.shared.controls.elements.Chip
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.core.designsystem.shared.pages.components.belowControlPanel
import com.otusfoss.core.designsystem.shared.pages.components.pageSideInset
import com.otusfoss.core.designsystem.shared.panels.components.ChipPlacement
import com.otusfoss.core.designsystem.shared.panels.components.ContextPanel
import com.otusfoss.core.designsystem.shared.panels.elements.PanelNavigation
import com.otusfoss.core.designsystem.theme.tokens.layout.PageTokens
import com.otusfoss.core.designsystem.theme.typography.panelTextStyle
import com.otusfoss.mail.feature.contacts.domain.CardKey
import com.otusfoss.mail.feature.contacts.domain.CardSync
import com.otusfoss.mail.feature.contacts.domain.ContactAddress
import com.otusfoss.mail.feature.contacts.domain.ContactPerson
import com.otusfoss.mail.feature.contacts.domain.MergePlan
import com.otusfoss.mail.feature.contacts.presentation.ContactNavigation
import com.otusfoss.mail.feature.contacts.presentation.ContactPage
import com.otusfoss.mail.feature.contacts.presentation.ContactsState
import com.otusfoss.mail.feature.contacts.presentation.book
import com.otusfoss.mail.feature.contacts.presentation.canWrite
import com.otusfoss.mail.feature.contacts.presentation.card
import com.otusfoss.mail.feature.contacts.presentation.components.ContactAvatarSheet
import com.otusfoss.mail.feature.contacts.presentation.components.ContactDetails
import com.otusfoss.mail.feature.contacts.presentation.components.ContactOverlay
import com.otusfoss.mail.feature.contacts.presentation.components.ContactOverlaySheet
import com.otusfoss.mail.feature.contacts.presentation.components.ContactPageBar
import com.otusfoss.mail.feature.contacts.presentation.components.GroupMembers
import com.otusfoss.mail.feature.contacts.presentation.components.PersonFacts
import com.otusfoss.mail.feature.contacts.presentation.contactPage
import com.otusfoss.mail.feature.contacts.presentation.current
import com.otusfoss.mail.feature.contacts.presentation.groupsOf
import com.otusfoss.mail.feature.contacts.presentation.isOwnAvatar
import com.otusfoss.mail.feature.contacts.presentation.look
import com.otusfoss.mail.feature.contacts.presentation.plainTextClip
import com.otusfoss.mail.feature.contacts.presentation.removeFromGroup
import com.otusfoss.mail.feature.contacts.presentation.saveElsewhere
import com.otusfoss.mail.feature.contacts.presentation.setAvatar
import kotlinx.coroutines.launch

/**
 * One person, or a group's members. The panel holds the name with Edit and, for a person shown
 * from several cards, the cards. The bar writes to the preferred address; its context lists the
 * others. A card that disappears, such as one deleted elsewhere, closes the page.
 */
@Composable
internal fun ContactScreen(
    state: ContactsState,
    opened: CardKey,
    navigation: ContactNavigation,
) {
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = navigation.onBack)
    val key = state.current(opened)
    val card = state.card(key)
    LaunchedEffect(card == null, state.snapshot) { if (card == null && state.snapshot != null) navigation.onBack() }
    card ?: return
    val page = state.contactPage(key, card)
    var overlay by remember { mutableStateOf<ContactOverlay?>(null) }
    ProvideTextStyle(panelTextStyle()) {
        ActionBarHost(
            bar = { ContactPageBar(page, state, navigation) { overlay = it } },
            Modifier.fillMaxSize().belowControlPanel(),
        ) { barPadding ->
            Column(Modifier.fillMaxSize()) {
                ContactPanel(page, state, navigation.onBack) { overlay = ContactOverlay.Merge(it) }
                Box(
                    Modifier
                        .weight(1f)
                        .pageSideInset()
                        .verticalScroll(rememberScrollState())
                        .padding(barPadding)
                        .padding(vertical = PageTokens.contentInset),
                ) {
                    ContactBody(page, state, navigation)
                }
            }
        }
    }
    overlay?.let { ContactOverlaySheet(it, page, state, navigation.onBack) { next -> overlay = next } }
}

/**
 * The page's panel: Back, the name, and a chip to merge when the person comes from several cards.
 * [onMerge] gets the plan, when the cards can be merged at all.
 */
@Composable
private fun ContactPanel(
    page: ContactPage,
    state: ContactsState,
    onBack: () -> Unit,
    onMerge: (MergePlan) -> Unit,
) {
    val cards = page.person?.cards.orEmpty()
    val mergeCards = {
        val plan = MergePlan.of(cards, state.writableBooks)
        if (plan != null) onMerge(plan)
    }
    ContextPanel(
        placement = if (cards.size > 1) ChipPlacement.Overlapping else ChipPlacement.None,
        navigation = { PanelNavigation("Back", onBack) },
        chips = {
            if (cards.size > 1) {
                item("cards") {
                    Chip("Merge ${cards.size} cards", mergeCards) {
                        Symbol(Res.drawable.symbol_call_merge)
                    }
                }
            }
        },
    ) { Text(page.name) }
}

/** The scrolling body: a group's members, or a person's details. */
@Composable
private fun ContactBody(
    page: ContactPage,
    state: ContactsState,
    navigation: ContactNavigation,
) {
    val person = page.person
    when {
        page.card.isGroup -> GroupBody(page, state, navigation.onOpen)
        person != null -> PersonBody(person, page.name, state, navigation)
    }
}

/** A group's members, with its avatar and removal editable when the group can change. */
@Composable
private fun GroupBody(
    page: ContactPage,
    state: ContactsState,
    onOpen: (CardKey) -> Unit,
) {
    val card = page.card
    var editing by remember { mutableStateOf(false) }
    GroupMembers(
        card,
        page.members,
        onOpen,
        { member: ContactPerson -> state.removeFromGroup(card, member) }.takeIf { page.writable },
        onEditAvatar = { editing = true }.takeIf { page.writable },
    )
    if (editing) {
        ContactAvatarSheet(card.displayName, null, card.look, state.photoPicker, { state.setAvatar(card, it) }) {
            editing = false
        }
    }
}

/**
 * A person's details. A change the server refused offers to save the card to another writable
 * book, one the refused card is not in.
 */
@Composable
private fun PersonBody(
    person: ContactPerson,
    name: String,
    state: ContactsState,
    navigation: ContactNavigation,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val refusedIn = person.cards.firstOrNull { it.sync is CardSync.Refused }
    val saveTo = state.writableBooks.firstOrNull { b -> refusedIn?.books?.contains(b.key) == false }
    val main = person.main
    val writable = state.canWrite(main)
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        ContactAvatarSheet(
            name,
            main.emails.firstOrNull()?.value,
            main.look,
            state.photoPicker,
            { state.setAvatar(main, it) },
        ) { editing = false }
    }
    ContactDetails(
        PersonFacts(
            person,
            booksOf = { c -> c.books.mapNotNull(state::book) },
            groups = person.cards.flatMap(state::groupsOf).distinctBy { it.key },
            saveElsewhere = saveTo,
            ownAvatar = person.isOwnAvatar(state.ownAddresses()),
        ),
        onEditAvatar = { editing = true }.takeIf { writable },
        onWriteTo = { address -> navigation.onWrite(listOf(ContactAddress(name, address))) },
        onCopyNumber = { number ->
            scope.launch { clipboard.setClipEntry(plainTextClip("Phone number", number)) }
            state.notice = "Copied phone number"
        },
        onOpen = navigation.onOpen,
        onSaveElsewhere = { if (refusedIn != null && saveTo != null) state.saveElsewhere(refusedIn.key, saveTo.key) },
    )
}
