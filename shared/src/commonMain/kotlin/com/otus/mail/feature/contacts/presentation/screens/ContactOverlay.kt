package com.otus.mail.feature.contacts.presentation.screens

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.ConfirmDialog
import com.otus.mail.feature.contacts.domain.MergePlan
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.components.GroupEdit
import com.otus.mail.feature.contacts.presentation.components.GroupEditSheet
import com.otus.mail.feature.contacts.presentation.components.MergeSheet
import com.otus.mail.feature.contacts.presentation.delete
import com.otus.mail.feature.contacts.presentation.merge
import com.otus.mail.feature.contacts.presentation.showSeparately

/** The dialog or sheet open over a contact page; the page shows at most one at a time. */
internal sealed interface ContactOverlay {
    /** Asks before deleting the page's cards from the server. */
    data object Delete : ContactOverlay

    /** Adds people to the group or renames it. */
    data class EditGroup(
        val edit: GroupEdit,
    ) : ContactOverlay

    /** Merges the person's cards, following [plan] as the user changes it. */
    data class Merge(
        val plan: MergePlan,
    ) : ContactOverlay
}

/** Shows [overlay] over [page]; [onChange] swaps it, or closes it with null. */
@Composable
internal fun ContactOverlaySheet(
    overlay: ContactOverlay,
    page: ContactPage,
    state: ContactsState,
    onBack: () -> Unit,
    onChange: (ContactOverlay?) -> Unit,
) {
    when (overlay) {
        ContactOverlay.Delete -> {
            ConfirmDialog(
                title = "Delete “${page.name}”?",
                message = "It’s removed from your server, so every app loses it.",
                confirmLabel = "Delete",
                onConfirm = {
                    onChange(null)
                    state.delete(page.cards.map { it.key })
                    onBack()
                },
                onDismiss = { onChange(null) },
            )
        }

        is ContactOverlay.EditGroup -> {
            GroupEditSheet(overlay.edit, state, page.card, page.members) { onChange(null) }
        }

        is ContactOverlay.Merge -> {
            MergeSheet(
                overlay.plan,
                state.writableBooks,
                onChange = { onChange(ContactOverlay.Merge(it)) },
                "Show separately",
                onAlternative = {
                    state.showSeparately(overlay.plan.cards.map { it.key })
                    onChange(null)
                },
                onMerge = {
                    state.merge(it)
                    onChange(null)
                },
                onDismiss = { onChange(null) },
            )
        }
    }
}
