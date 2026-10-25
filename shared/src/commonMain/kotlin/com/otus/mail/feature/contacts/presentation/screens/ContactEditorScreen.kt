package com.otus.mail.feature.contacts.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.designsystem.components.ActionBarHost
import com.otus.designsystem.components.ContextPanel
import com.otus.designsystem.elements.IconButton
import com.otus.designsystem.elements.IconButtonLook
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_close
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.panelTextStyle
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactDraft
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.card
import com.otus.mail.feature.contacts.presentation.components.ContactEditorBar
import com.otus.mail.feature.contacts.presentation.components.ContactForm
import com.otus.mail.feature.contacts.presentation.components.ContactLabels
import com.otus.mail.feature.contacts.presentation.components.editorBarContext
import com.otus.mail.feature.contacts.presentation.deleteLabel
import com.otus.mail.feature.contacts.presentation.photoChoices
import com.otus.mail.feature.contacts.presentation.save
import com.otus.mail.feature.contacts.presentation.withLabel
import kotlinx.coroutines.launch

/**
 * Creates a contact, or edits the card [key]. The bar saves; its context names the address book
 * and opens the others the card can go to. ⋯ holds Save and add another and import. While the
 * user types, the bar minimizes, so it never covers the field above the keyboard.
 */
@Composable
internal fun ContactEditorScreen(
    state: ContactsState,
    key: CardKey?,
    onClose: () -> Unit,
    onSaved: (CardKey) -> Unit,
) {
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onClose)
    val existing = key?.let(state::card)
    var draft by remember(key) { mutableStateOf(state.startDraft(existing)) }
    var book by remember(key) { mutableStateOf(existing?.books?.firstOrNull() ?: state.defaultBook?.key) }
    val save: (Boolean) -> Unit = { andNext ->
        book?.let { target ->
            val saved = state.save(draft, existing, target)
            if (andNext) draft = ContactDraft() else onSaved(saved)
        }
    }
    val scope = rememberCoroutineScope()
    val photoChoices =
        photoChoices(
            hasPhoto = draft.photo != null,
            canPick = state.canPickPhoto,
            onPick = { scope.launch { state.pickPhoto()?.let { draft = draft.copy(photo = it) } } },
            onRemove = { draft = draft.copy(photo = null) },
        )
    ProvideTextStyle(panelTextStyle()) {
        ActionBarHost(
            bar = {
                ContactEditorBar(
                    draft,
                    editorBarContext(draft, state.targetBooks(existing), book) { book = it },
                    isNew = existing == null,
                    onSave = save,
                    onImport = { state.pickImport() }.takeIf { state.canTransfer },
                    onDiscard = onClose,
                )
            },
            Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        ) { barPadding ->
            Column(Modifier.fillMaxSize().padding(horizontal = PageTokens.pageInset)) {
                EditorPanel(isNew = existing == null, onClose)
                ContactForm(
                    draft,
                    { draft = it },
                    ContactLabels(state.customLabels, { state.withLabel(it).size }, state::deleteLabel),
                    photoChoices = photoChoices,
                    modifier =
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(barPadding)
                            .padding(vertical = PageTokens.contentInset),
                )
            }
        }
    }
}

/** The panel over the form: its title and ✕, which discards like the bar's Discard. */
@Composable
private fun EditorPanel(
    isNew: Boolean,
    onClose: () -> Unit,
) {
    ContextPanel(
        Modifier.padding(top = PageTokens.pageInset),
        navigation = {
            IconButton("Discard", onClose, look = IconButtonLook.Plain) { Symbol(Res.drawable.symbol_close) }
        },
    ) { Text(if (isNew) "New contact" else "Edit contact") }
}

/** The draft the editor opens with: the card's own fields, else a prefill handed over once, else blank. */
private fun ContactsState.startDraft(existing: ContactCard?): ContactDraft =
    existing?.let(ContactDraft::of) ?: prefill.also { prefill = null } ?: ContactDraft()

/** The books the card can be saved to: any writable one for a new card, else one in the card's own account. */
private fun ContactsState.targetBooks(existing: ContactCard?): List<ContactBook> =
    writableBooks.filter {
        existing == null || (it.key.loginId == existing.key.loginId && it.key.accountId == existing.key.accountId)
    }
