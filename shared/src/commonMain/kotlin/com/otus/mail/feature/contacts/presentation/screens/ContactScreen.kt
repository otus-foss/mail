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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.designsystem.components.ActionBar
import com.otus.designsystem.components.ActionBarHost
import com.otus.designsystem.components.BarAction
import com.otus.designsystem.components.BarContext
import com.otus.designsystem.components.BarToken
import com.otus.designsystem.components.ChipPlacement
import com.otus.designsystem.components.ConfirmDialog
import com.otus.designsystem.components.ContextPanel
import com.otus.designsystem.components.MenuAction
import com.otus.designsystem.elements.Chip
import com.otus.designsystem.elements.IconButton
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_chevron_left
import com.otus.designsystem.resources.symbol_contacts
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_edit
import com.otus.designsystem.resources.symbol_mail
import com.otus.designsystem.resources.symbol_person_edit
import com.otus.designsystem.resources.symbol_upload
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.PanelTokens
import com.otus.designsystem.theme.panelTextStyle
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.CardSync
import com.otus.mail.feature.contacts.domain.MergePlan
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.avatar
import com.otus.mail.feature.contacts.presentation.components.ContactDetails
import com.otus.mail.feature.contacts.presentation.components.GroupMembers
import com.otus.mail.feature.contacts.presentation.components.MergeSheet
import com.otus.mail.feature.contacts.presentation.components.PersonFacts
import com.otus.mail.feature.contacts.presentation.peopleCount
import com.otus.mail.feature.inbox.domain.MailSender

/** Where one person's page leads. */
internal class ContactNavigation(
    val onBack: () -> Unit,
    val onEdit: (CardKey) -> Unit,
    val onOpen: (CardKey) -> Unit,
    val onWrite: (List<MailSender>) -> Unit,
)

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
    val person = state.person(key)
    val members = if (card.isGroup) state.members(card) else emptyList()
    val name = person?.name ?: card.displayName
    val emails = person?.emails.orEmpty()
    val writable = (person?.cards ?: listOf(card)).all { c -> c.books.all { state.book(it)?.canWrite == true } }
    var merging by remember { mutableStateOf<MergePlan?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val write = { addresses: List<MailSender> -> navigation.onWrite(addresses) }
    ProvideTextStyle(panelTextStyle()) {
        ActionBarHost(
            bar = {
                val recipients =
                    if (card.isGroup) {
                        members.mapNotNull { p -> p.emails.firstOrNull()?.let { MailSender(p.name, it.value) } }
                    } else {
                        emails.take(1).map { MailSender(name, it.value) }
                    }
                ActionBar(
                    BarAction(
                        "Write",
                        Res.drawable.symbol_edit,
                        enabled = recipients.isNotEmpty(),
                    ) { write(recipients) },
                    secondary =
                        listOfNotNull(
                            BarAction(
                                "Edit",
                                Res.drawable.symbol_person_edit,
                            ) { navigation.onEdit(key) }.takeIf { writable },
                            BarAction("Export as vCard", Res.drawable.symbol_upload) {
                                state.export(person?.cards ?: listOf(card))
                            }.takeIf { state.canTransfer },
                            BarAction("Delete", Res.drawable.symbol_delete) { deleting = true }.takeIf { writable },
                        ),
                    context =
                        when {
                            card.isGroup -> {
                                BarContext(
                                    peopleCount(recipients.size),
                                    BarToken.Count(recipients.size),
                                    "to ${peopleCount(recipients.size)}",
                                )
                            }

                            emails.isEmpty() -> {
                                null
                            }

                            else -> {
                                BarContext(
                                    emails.first().value,
                                    BarToken.Person(person?.avatar ?: card.avatar),
                                    "to ${emails.first().value}",
                                    choices =
                                        emails.drop(1).map { email ->
                                            MenuAction(
                                                "Write to ${email.value}",
                                                Res.drawable.symbol_mail,
                                            ) { write(listOf(MailSender(name, email.value))) }
                                        },
                                )
                            }
                        },
                )
            },
            Modifier.fillMaxSize().safeDrawingPadding(),
        ) { barPadding ->
            Column(Modifier.fillMaxSize().padding(horizontal = PageTokens.pageInset)) {
                ContextPanel(
                    Modifier.padding(top = PageTokens.pageInset),
                    placement = if ((person?.cards?.size ?: 0) > 1) ChipPlacement.Overlapping else ChipPlacement.None,
                    navigation = {
                        IconButton("Back", navigation.onBack, outlined = false) {
                            Symbol(
                                Res.drawable.symbol_chevron_left,
                                mirrorInRtl = true,
                                size = PanelTokens.navigationIconSize,
                            )
                        }
                    },
                    chips = {
                        val cards = person?.cards.orEmpty()
                        if (cards.size > 1) {
                            item("cards") {
                                Chip("${cards.size} cards", { merging = MergePlan.of(cards, state.writableBooks) }) {
                                    Symbol(Res.drawable.symbol_contacts)
                                }
                            }
                        }
                    },
                ) { Text(name) }
                Box(
                    Modifier
                        .weight(
                            1f,
                        ).verticalScroll(
                            rememberScrollState(),
                        ).padding(barPadding)
                        .padding(vertical = PageTokens.contentInset),
                ) {
                    if (card.isGroup) {
                        GroupMembers(card, members, navigation.onOpen)
                    } else if (person != null) {
                        val refusedIn = person.cards.firstOrNull { it.sync is CardSync.Refused }
                        ContactDetails(
                            PersonFacts(
                                person,
                                booksOf = { c -> c.books.mapNotNull(state::book) },
                                groups = person.cards.flatMap(state::groupsOf).distinctBy { it.key },
                                saveElsewhere =
                                    state.writableBooks.firstOrNull { b ->
                                        refusedIn?.books?.contains(b.key) ==
                                            false
                                    },
                            ),
                            onWriteTo = { address -> write(listOf(MailSender(name, address))) },
                            onOpen = navigation.onOpen,
                            onSaveElsewhere = {
                                val book =
                                    state.writableBooks.firstOrNull { b ->
                                        refusedIn?.books?.contains(b.key) ==
                                            false
                                    }
                                if (refusedIn != null && book != null) state.saveElsewhere(refusedIn.key, book.key)
                            },
                        )
                    }
                }
            }
        }
    }
    if (deleting) {
        ConfirmDialog(
            title = "Delete “$name”?",
            message = "It’s removed from your server, so every app loses it.",
            confirmLabel = "Delete",
            onConfirm = {
                deleting = false
                state.delete((person?.cards ?: listOf(card)).map { it.key })
                navigation.onBack()
            },
            onDismiss = { deleting = false },
        )
    }
    merging?.let { plan ->
        MergeSheet(plan, state.writableBooks, onChange = { merging = it }, onShowAsOne = { merging = null }, onMerge = {
            state.merge(it)
            merging = null
        }, onDismiss = { merging = null })
    }
}
