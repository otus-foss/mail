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
import com.otus.designsystem.components.ActionBar
import com.otus.designsystem.components.ActionBarHost
import com.otus.designsystem.components.BarAction
import com.otus.designsystem.components.BarContext
import com.otus.designsystem.components.BarMinimized
import com.otus.designsystem.components.BarToken
import com.otus.designsystem.components.ContextPanel
import com.otus.designsystem.components.MenuAction
import com.otus.designsystem.components.OptionsItem
import com.otus.designsystem.elements.IconButton
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.elements.isKeyboardUp
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_check
import com.otus.designsystem.resources.symbol_close
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_download
import com.otus.designsystem.resources.symbol_hide_image
import com.otus.designsystem.resources.symbol_image
import com.otus.designsystem.resources.symbol_more_horiz
import com.otus.designsystem.resources.symbol_person_add
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.panelTextStyle
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactDraft
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.components.ContactForm
import com.otus.mail.feature.contacts.presentation.components.ContactLabels
import com.otus.mail.feature.contacts.presentation.deleteLabel
import com.otus.mail.feature.contacts.presentation.icon
import com.otus.mail.feature.contacts.presentation.nameAvatar
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
    var draft by remember(key) {
        mutableStateOf(existing?.let(ContactDraft::of) ?: state.prefill.also { state.prefill = null } ?: ContactDraft())
    }
    var book by remember(key) { mutableStateOf(existing?.books?.firstOrNull() ?: state.defaultBook?.key) }
    val books =
        state.writableBooks.filter {
            existing == null ||
                (it.key.loginId == existing.key.loginId && it.key.accountId == existing.key.accountId)
        }
    val save = { andNext: Boolean ->
        book?.let { target ->
            val saved = state.save(draft, existing, target)
            if (andNext) {
                draft = ContactDraft()
            } else {
                onSaved(saved)
            }
        }
    }
    val scope = rememberCoroutineScope()
    val photoChoices =
        listOfNotNull(
            OptionsItem
                .Action(if (draft.photo == null) "Choose photo" else "Change photo", Res.drawable.symbol_image, {
                    scope.launch { state.pickPhoto()?.let { draft = draft.copy(photo = it) } }
                })
                .takeIf { state.canPickPhoto },
            OptionsItem
                .Action("Remove photo", Res.drawable.symbol_hide_image, { draft = draft.copy(photo = null) })
                .takeIf { draft.photo != null },
        )
    val typing = isKeyboardUp()
    ProvideTextStyle(panelTextStyle()) {
        ActionBarHost(
            bar = {
                val chosen = books.firstOrNull { it.key == book }
                val name =
                    draft.fullName.ifEmpty {
                        draft.emails
                            .firstOrNull()
                            ?.value
                            .orEmpty()
                    }
                ActionBar(
                    BarAction(
                        "Save",
                        Res.drawable.symbol_check,
                        enabled = draft.canSave && chosen != null,
                    ) { save(false) },
                    secondary =
                        listOfNotNull(
                            BarAction("Discard", Res.drawable.symbol_delete, onClick = onClose),
                            BarAction(
                                "More options",
                                Res.drawable.symbol_more_horiz,
                                menu =
                                    listOfNotNull(
                                        MenuAction(
                                            "Save and add another",
                                            Res.drawable.symbol_person_add,
                                        ) { save(true) }
                                            .takeIf { existing == null && draft.canSave },
                                        MenuAction(
                                            "Import from a vCard file",
                                            Res.drawable.symbol_download,
                                        ) { state.pickImport() }
                                            .takeIf { existing == null && state.canTransfer },
                                    ),
                            ) {}.takeIf { existing == null },
                        ),
                    context =
                        chosen?.let {
                            BarContext(
                                "To ${it.name}",
                                BarToken.Person(nameAvatar(name.ifEmpty { "?" })),
                                "Save to ${it.name}. Change the address book",
                                choices =
                                    books.filter { b -> b.key != book }.map { b ->
                                        MenuAction("Save to ${b.name}", b.icon) {
                                            book =
                                                b.key
                                        }
                                    },
                            )
                        },
                    minimized = BarMinimized().takeIf { typing },
                )
            },
            Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        ) { barPadding ->
            Column(Modifier.fillMaxSize().padding(horizontal = PageTokens.pageInset)) {
                ContextPanel(
                    Modifier.padding(top = PageTokens.pageInset),
                    navigation = {
                        IconButton("Discard", onClose, outlined = false) { Symbol(Res.drawable.symbol_close) }
                    },
                ) { Text(if (existing == null) "New contact" else "Edit contact") }
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
