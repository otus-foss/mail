package com.otusfoss.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_check
import com.otusfoss.core.designsystem.resources.symbol_delete
import com.otusfoss.core.designsystem.resources.symbol_download
import com.otusfoss.core.designsystem.resources.symbol_more_horiz
import com.otusfoss.core.designsystem.resources.symbol_person_add
import com.otusfoss.core.designsystem.shared.actionbar.components.ActionBar
import com.otusfoss.core.designsystem.shared.actionbar.components.BarAction
import com.otusfoss.core.designsystem.shared.actionbar.components.BarContext
import com.otusfoss.core.designsystem.shared.actionbar.components.BarMinimized
import com.otusfoss.core.designsystem.shared.actionbar.components.BarToken
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.input.elements.isKeyboardUp
import com.otusfoss.mail.feature.contacts.domain.BookKey
import com.otusfoss.mail.feature.contacts.domain.ContactBook
import com.otusfoss.mail.feature.contacts.domain.ContactDraft
import com.otusfoss.mail.feature.contacts.presentation.icon
import com.otusfoss.mail.feature.contacts.presentation.nameAvatar

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
            UiAction("Save", Res.drawable.symbol_check, enabled = draft.canSave && context != null) { onSave(false) },
        ),
        secondary =
            listOfNotNull(
                BarAction(UiAction("Discard", Res.drawable.symbol_delete, onClick = onDiscard)),
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
    UiAction("More options", Res.drawable.symbol_more_horiz) {},
    menu =
        listOfNotNull(
            UiAction("Save and add another", Res.drawable.symbol_person_add) { onSave(true) }
                .takeIf { draft.canSave },
            onImport?.let { UiAction("Import from a vCard file", Res.drawable.symbol_download, onClick = it) },
        ),
)

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
            draft.emails
                .firstOrNull()
                ?.value
                .orEmpty()
        }
    return books.firstOrNull { it.key == chosen }?.let { book ->
        BarContext(
            "To ${book.name}",
            BarToken.Person(nameAvatar(name.ifEmpty { "?" })),
            "Save to ${book.name}. Change the address book",
            choices =
                books
                    .filter { it.key != chosen }
                    .map { other -> UiAction("Save to ${other.name}", other.icon) { onChoose(other.key) } },
        )
    }
}
