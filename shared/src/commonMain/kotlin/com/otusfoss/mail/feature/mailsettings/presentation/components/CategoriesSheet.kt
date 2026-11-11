package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_all_inbox
import com.otusfoss.core.designsystem.shared.overlays.components.SheetFrame
import com.otusfoss.core.designsystem.shared.pages.components.PageNote
import com.otusfoss.core.designsystem.shared.settings.components.SettingToggleRow
import com.otusfoss.mail.core.categories.domain.InboxCategories
import com.otusfoss.mail.core.categories.domain.MailCategory
import com.otusfoss.mail.core.categories.presentation.description
import com.otusfoss.mail.core.categories.presentation.label
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState

/**
 * Turns the Inbox's category pills on and off, and shows or hides each category but Primary. Mail
 * of a hidden category shows in Primary. Each switch is saved at once, and Done closes the sheet.
 */
@Composable
fun CategoriesSheet(
    state: MailBehaviorState,
    onDismiss: () -> Unit,
) {
    val categories = state.behavior.list.categories
    val change: ((InboxCategories) -> InboxCategories) -> Unit = { update ->
        state.updateList { it.copy(categories = update(it.categories)) }
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
        PageNote(
            "Mail stays in your Inbox. It is sorted on this device from its headers and the text it " +
                "has; nothing is sent anywhere. A hidden category's mail shows in Primary.",
        )
    }
}

/** The Categories row's value: Off, or how many pills the Inbox shows. */
internal fun categoriesSummary(categories: InboxCategories): String =
    if (categories.enabled) "${categories.tabs.size} shown" else "Off"
