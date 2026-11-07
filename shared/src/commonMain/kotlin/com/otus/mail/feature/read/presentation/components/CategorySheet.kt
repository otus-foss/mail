package com.otus.mail.feature.read.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.mail.core.categories.domain.MailCategory
import com.otus.mail.core.categories.presentation.description
import com.otus.mail.core.categories.presentation.explanation
import com.otus.mail.core.categories.presentation.label
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.shownName
import com.otus.mail.feature.read.presentation.ReadCategories

/**
 * Where mail from [row]'s sender goes: Automatic, the classifier's choice, or one of the shown
 * categories, which then holds all of it. A saved choice of a category the user later hid stays
 * listed, so the check still shows it. The note under the choices says why the Inbox shows [row]
 * where it does. A choice applies at once and moves the check.
 */
@Composable
fun CategorySheet(
    row: MailMessage,
    categories: ReadCategories,
    onDismiss: () -> Unit,
) {
    val settings = categories.settings()
    val address = row.sender.address
    val chosen = settings.senderChoice(address)
    val choices =
        listOf(CategoryChoice(null)) +
            MailCategory.entries
                .filter { settings.shows(it) || it == chosen }
                .map(::CategoryChoice)
    ChoiceSheet(
        "Mail from ${row.sender.shownName}",
        ChoiceOptions(choices, CategoryChoice(chosen), default = CategoryChoice(null)),
        row = { ChoiceRow(it.label, it.detail) },
        onSelect = { categories.choose(address, it.category) },
        onDismiss = onDismiss,
        footnote = settings.placement(address, row.category).explanation(),
    )
}

/** One choice of the sheet; a null [category] lets Mail decide. */
private data class CategoryChoice(
    val category: MailCategory?,
) {
    val label: String
        get() = category?.label ?: "Automatic"

    val detail: String
        get() = category?.description ?: "Mail sorts each message on this device"
}
