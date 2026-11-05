package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_all_inbox
import com.otus.designsystem.shared.overlays.components.SheetFrame
import com.otus.designsystem.shared.pages.components.PageNote
import com.otus.designsystem.shared.settings.components.SettingToggleRow
import com.otus.mail.feature.inbox.domain.category.InboxCategories
import com.otus.mail.feature.inbox.domain.category.MailCategory
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.description
import com.otus.mail.feature.inbox.presentation.label

/**
 * Turns the Inbox's category pills on and off, and shows or hides each category but Primary. Mail
 * of a hidden category shows in Primary. Each switch is saved at once, and Done closes the sheet.
 */
@Composable
internal fun CategoriesSheet(
    state: MailBehaviorState,
    onDismiss: () -> Unit,
) {
    val categories = state.behavior.categories
    val change: ((InboxCategories) -> InboxCategories) -> Unit = { update ->
        state.update { it.copy(categories = update(it.categories)) }
    }
    SheetFrame("Categories", onDismiss) {
        SettingToggleRow(
            "Sort into categories",
            checked = categories.enabled,
            onCheckedChange = { on -> change { it.copy(enabled = on) } },
            description = "Pills at the top of the Inbox",
            icon = Res.drawable.symbol_all_inbox,
        )
        MailCategory.entries.filter { it != MailCategory.Primary }.forEach { category ->
            SettingToggleRow(
                category.label,
                checked = categories.shows(category),
                onCheckedChange = { on -> change { it.withShown(category, on) } },
                description = category.description,
            )
        }
        PageNote(CATEGORIES_NOTE)
    }
}

/** What the sheet says under its switches, and what settings search finds for it. */
internal const val CATEGORIES_NOTE =
    "Mail stays in your Inbox. It is sorted on this device from its headers and the text it has; " +
        "nothing is sent anywhere. A hidden category's mail shows in Primary."

/** The Categories row's value: Off, or how many pills the Inbox shows. */
internal fun categoriesSummary(categories: InboxCategories): String =
    if (categories.enabled) "${categories.tabs.size} shown" else "Off"
