package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_check
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_download
import com.otus.designsystem.resources.symbol_more_horiz
import com.otus.designsystem.resources.symbol_person_add
import com.otus.designsystem.shared.actionbar.components.ActionBar
import com.otus.designsystem.shared.actionbar.components.BarAction
import com.otus.designsystem.shared.actionbar.components.BarContext
import com.otus.designsystem.shared.actionbar.components.BarMinimized
import com.otus.designsystem.shared.actionbar.components.BarToken
import com.otus.designsystem.shared.input.elements.isKeyboardUp
import com.otus.designsystem.shared.overlays.components.MenuAction
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.ContactDraft
import com.otus.mail.feature.contacts.presentation.icon
import com.otus.mail.feature.contacts.presentation.nameAvatar

/**
 * The editor's bar. Save needs a [context], since that names the book the card goes to. ⋯ shows
 * only for a new card, and [onImport] is null when import is not possible. The bar minimizes
 * while the keyboard is up, so it never covers the field being typed in.
 */
@Composable
internal fun ContactEditorBar(
    draft: ContactDraft,
    context: BarContext?,
    isNew: Boolean,
    onSave: (andNext: Boolean) -> Unit,
    onImport: (() -> Unit)?,
    onDiscard: () -> Unit,
) {
    ActionBar(
        BarAction(
            "Save",
            Res.drawable.symbol_check,
            enabled = draft.canSave && context != null,
        ) { onSave(false) },
        secondary =
            listOfNotNull(
                BarAction("Discard", Res.drawable.symbol_delete, onClick = onDiscard),
                moreAction(draft, onSave, onImport).takeIf { isNew },
            ),
        context = context,
        minimized = BarMinimized().takeIf { isKeyboardUp() },
    )
}

/** ⋯ on a new card: Save and add another once the draft can be saved, and import when allowed. */
private fun moreAction(
    draft: ContactDraft,
    onSave: (andNext: Boolean) -> Unit,
    onImport: (() -> Unit)?,
) = BarAction(
    "More options",
    Res.drawable.symbol_more_horiz,
    menu =
        listOfNotNull(
            MenuAction("Save and add another", Res.drawable.symbol_person_add) { onSave(true) }
                .takeIf { draft.canSave },
            onImport?.let { MenuAction("Import from a vCard file", Res.drawable.symbol_download, onClick = it) },
        ),
) {}

/**
 * The bar's context: the [chosen] book among [books], shown with the draft's name or first address,
 * and a menu that moves the card to another book. Null while no book is chosen.
 */
internal fun editorBarContext(
    draft: ContactDraft,
    books: List<ContactBook>,
    chosen: BookKey?,
    onChoose: (BookKey) -> Unit,
): BarContext? {
    val name =
        draft.fullName.ifEmpty {
            val first = draft.emails.firstOrNull()
            first?.value.orEmpty()
        }
    val book = books.firstOrNull { it.key == chosen } ?: return null
    return BarContext(
        "To ${book.name}",
        BarToken.Person(nameAvatar(name.ifEmpty { "?" })),
        "Save to ${book.name}. Change the address book",
        choices =
            books.filter { it.key != chosen }.map { other ->
                MenuAction("Save to ${other.name}", other.icon) { onChoose(other.key) }
            },
    )
}
