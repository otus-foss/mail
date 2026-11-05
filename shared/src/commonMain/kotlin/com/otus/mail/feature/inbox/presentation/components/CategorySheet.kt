package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.category.MailCategory
import com.otus.mail.feature.inbox.domain.shownName
import com.otus.mail.feature.inbox.presentation.ReadCategories
import com.otus.mail.feature.inbox.presentation.description
import com.otus.mail.feature.inbox.presentation.explanation
import com.otus.mail.feature.inbox.presentation.label

/**
 * Where mail from [row]'s sender goes: Automatic, the classifier's choice, or one of the shown
 * categories, which then holds all of it. The note under the choices says why the Inbox shows
 * [row] where it does. A choice applies at once and moves the check.
 */
@Composable
internal fun CategorySheet(
    row: MailMessage,
    categories: ReadCategories,
    onDismiss: () -> Unit,
) {
    val settings = categories.settings()
    val address = row.sender.address
    val choices = listOf(CategoryChoice(null)) + settings.tabs.map(::CategoryChoice)
    ChoiceSheet(
        "Mail from ${row.sender.shownName}",
        ChoiceOptions(choices, CategoryChoice(settings.senderChoice(address)), default = CategoryChoice(null)),
        row = { ChoiceRow(it.label, it.detail) },
        onSelect = { categories.choose(address, it.category) },
        onDismiss = onDismiss,
        footnote = settings.placement(row).explanation(),
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
