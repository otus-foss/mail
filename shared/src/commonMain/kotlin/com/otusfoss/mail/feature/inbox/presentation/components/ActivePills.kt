package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_folder
import com.otusfoss.core.designsystem.shared.controls.elements.Chip
import com.otusfoss.core.designsystem.shared.controls.elements.ChipLabel
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.core.designsystem.shared.filters.components.FolderStackChip
import com.otusfoss.core.designsystem.shared.filters.components.StackedFolder
import com.otusfoss.mail.core.messages.domain.FolderRef
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.presentation.icon
import com.otusfoss.mail.core.messages.presentation.label
import com.otusfoss.mail.core.settings.presentation.icon
import com.otusfoss.mail.core.settings.presentation.label
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.presentation.FilterAccount
import com.otusfoss.mail.feature.inbox.presentation.FilterOptions
import com.otusfoss.mail.feature.inbox.presentation.key
import com.otusfoss.mail.feature.inbox.presentation.personOf
import org.jetbrains.compose.resources.DrawableResource

/** A selected pill drawn as search draws its own; pressing it calls the given click. */
internal typealias ActivePill = @Composable (onClick: () -> Unit) -> Unit

internal fun symbolPill(
    label: String,
    icon: DrawableResource,
): ActivePill = { onClick -> Chip(label, onClick, selected = true) { Symbol(icon) } }

/** The chosen senders as the search People pill: avatars, and one sender's first name. */
internal fun fromPill(
    filters: InboxFilters,
    people: FilterPeople,
): ActivePill {
    val senders =
        filters.from.map { key ->
            MailSender(key.name, key.addresses.sorted().first(), avatar = people.senders.personOf(key)?.avatar)
        }
    return { PeopleFilterChip(senders, it) }
}

/**
 * The chosen recipients after a small "To", so the pill never looks like From: the user's own
 * addresses with their account's avatar, then the others; one recipient shows its address.
 */
internal fun toPill(
    filters: InboxFilters,
    options: FilterOptions,
    people: FilterPeople,
): ActivePill {
    val picked = pickedRecipients(filters, options, people.recipients).map { it.recipient }
    val single = picked.singleOrNull()?.name
    val description = single?.let { "To $it" } ?: "To ${picked.size} people"
    return { AvatarFilterChip(picked.map { it.avatar }, single, description, it, prefix = "To") }
}

/** The chosen account's avatar and name. */
internal fun accountPill(account: FilterAccount): ActivePill =
    { AvatarFilterChip(listOf(account.avatar), account.name, "${account.name} account", it) }

/** The chosen folders: overlapping folder glyphs, the first one's name, and how many others. */
internal fun foldersPill(
    refs: List<FolderRef>,
    options: FilterOptions,
): ActivePill {
    val labels = refs.map(options::folderLabel)
    val first = labels.first()
    val folders =
        refs.map { ref ->
            val folder = options.folder(ref.key)
            StackedFolder(folder?.color, folder?.icon ?: Res.drawable.symbol_folder)
        }
    val more = (refs.size - 1).takeIf { it > 0 }?.let { "+$it" }
    val description = "Folders: " + labels.joinToString(", ") { it.spoken }
    return { FolderStackChip(folders, ChipLabel(first.name, first.prefix), description, it, more = more) }
}
