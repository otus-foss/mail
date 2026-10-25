package com.otus.mail.feature.contacts.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import com.otus.designsystem.components.ActionBarHost
import com.otus.designsystem.components.ChipPlacement
import com.otus.designsystem.components.ContextPanel
import com.otus.designsystem.elements.Chip
import com.otus.designsystem.elements.IconButton
import com.otus.designsystem.elements.IconButtonLook
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_call_merge
import com.otus.designsystem.resources.symbol_chevron_left
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.PanelTokens
import com.otus.designsystem.theme.panelTextStyle
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.CardSync
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.domain.MergePlan
import com.otus.mail.feature.contacts.domain.photo
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.book
import com.otus.mail.feature.contacts.presentation.card
import com.otus.mail.feature.contacts.presentation.components.ContactDetails
import com.otus.mail.feature.contacts.presentation.components.GroupMembers
import com.otus.mail.feature.contacts.presentation.components.PersonFacts
import com.otus.mail.feature.contacts.presentation.groupsOf
import com.otus.mail.feature.contacts.presentation.photoChoices
import com.otus.mail.feature.contacts.presentation.plainTextClip
import com.otus.mail.feature.contacts.presentation.removeFromGroup
import com.otus.mail.feature.contacts.presentation.saveElsewhere
import com.otus.mail.feature.contacts.presentation.setPhoto
import com.otus.mail.feature.inbox.domain.MailSender
import kotlinx.coroutines.launch

/**
 * One person, or a group's members. The panel holds the name with Edit and, for a person shown
 * from several cards, the cards. The bar writes to the preferred address; its context lists the
 * others. A card that disappears, such as one deleted elsewhere, closes the page.
 */
@Composable
internal fun ContactScreen(
    state: ContactsState,
    key: CardKey,
    navigation: ContactNavigation,
) {
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = navigation.onBack)
    val card = state.card(key)
    LaunchedEffect(card == null, state.snapshot) { if (card == null && state.snapshot != null) navigation.onBack() }
    card ?: return
    val page = state.contactPage(key, card)
    var overlay by remember { mutableStateOf<ContactOverlay?>(null) }
    ProvideTextStyle(panelTextStyle()) {
        ActionBarHost(
            bar = { ContactPageBar(page, state, navigation) { overlay = it } },
            Modifier.fillMaxSize().safeDrawingPadding(),
        ) { barPadding ->
            Column(Modifier.fillMaxSize().padding(horizontal = PageTokens.pageInset)) {
                ContactPanel(page, state, navigation.onBack) { overlay = ContactOverlay.Merge(it) }
                Box(
                    Modifier
                        .weight(1f)
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
    ContextPanel(
        Modifier.padding(top = PageTokens.pageInset),
        placement = if (cards.size > 1) ChipPlacement.Overlapping else ChipPlacement.None,
        navigation = {
            IconButton("Back", onBack, look = IconButtonLook.Plain) {
                Symbol(Res.drawable.symbol_chevron_left, mirrorInRtl = true, size = PanelTokens.navigationIconSize)
            }
        },
        chips = {
            if (cards.size > 1) {
                item("cards") {
                    Chip("Merge ${cards.size} cards", { MergePlan.of(cards, state.writableBooks)?.let(onMerge) }) {
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

/** A group's members, with photo choices and removal when the group can change. */
@Composable
private fun GroupBody(
    page: ContactPage,
    state: ContactsState,
    onOpen: (CardKey) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val card = page.card
    GroupMembers(
        card,
        page.members,
        onOpen,
        { member: ContactPerson -> state.removeFromGroup(card, member) }.takeIf { page.writable },
        photoChoices =
            if (page.writable) {
                photoChoices(
                    hasPhoto = card.card.photo != null,
                    canPick = state.canPickPhoto,
                    onPick = { scope.launch { state.pickPhoto()?.let { state.setPhoto(card, it) } } },
                    onRemove = { state.setPhoto(card, null) },
                )
            } else {
                emptyList()
            },
    )
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
    ContactDetails(
        PersonFacts(
            person,
            booksOf = { c -> c.books.mapNotNull(state::book) },
            groups = person.cards.flatMap(state::groupsOf).distinctBy { it.key },
            saveElsewhere = saveTo,
        ),
        onWriteTo = { address -> navigation.onWrite(listOf(MailSender(name, address))) },
        onCopyNumber = { number ->
            scope.launch { clipboard.setClipEntry(plainTextClip("Phone number", number)) }
            state.notice = "Copied phone number"
        },
        onOpen = navigation.onOpen,
        onSaveElsewhere = { if (refusedIn != null && saveTo != null) state.saveElsewhere(refusedIn.key, saveTo.key) },
    )
}
