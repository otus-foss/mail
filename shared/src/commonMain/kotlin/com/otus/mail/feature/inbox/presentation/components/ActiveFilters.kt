package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.elements.Chip
import com.otus.designsystem.elements.FolderGlyph
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_alternate_email
import com.otus.designsystem.resources.symbol_calendar_add_on
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_folder_off
import com.otus.designsystem.resources.symbol_straighten
import com.otus.designsystem.theme.PanelTokens
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.SizeFilter
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.FolderLabel
import com.otus.mail.feature.inbox.presentation.key
import org.jetbrains.compose.resources.DrawableResource

/** A selected pill drawn as search draws its own; pressing it calls the given click. */
internal typealias ActivePill = @Composable (onClick: () -> Unit) -> Unit

/**
 * One applied choice as its [pill]: senders, accounts, and addresses each share one avatar pill,
 * as in search. [remove] gives the filters without it.
 */
internal class ActiveFacet(
    val key: Any,
    val remove: (InboxFilters) -> InboxFilters,
    val pill: ActivePill,
)

/**
 * A page's [applied] filters for the pill row, which [onChange] replaces. [button] focuses the
 * filter button, which the panel gives focus back to when it closes.
 */
class ActiveFilters(
    val applied: InboxFilters = InboxFilters(),
    val options: FilterOptions = FilterOptions(),
    val dateFormatter: DateFormatter = DateFormatter(),
    val onChange: (InboxFilters) -> Unit = {},
    val button: FocusRequester = FocusRequester(),
) {
    /** Each applied choice, in the panel's order; the sort order is not one. */
    internal val active: List<ActiveFacet>
        get() =
            buildList {
                applied.show.sortedBy { it.ordinal }.forEach { facet ->
                    add(ActiveFacet(facet, { it.with(facet, false) }, symbolPill(facet.label, facet.icon)))
                }
                applied.from.takeIf { it.isNotEmpty() }?.let { from ->
                    val senders = from.map { MailSender(name = "", address = it) }
                    add(ActiveFacet(FROM_KEY, { it.copy(from = emptySet()) }) { PeopleFilterChip(senders, it) })
                }
                applied.accounts.takeIf { it.isNotEmpty() }?.let { ids ->
                    add(ActiveFacet(ACCOUNTS_KEY, { it.copy(accounts = emptySet()) }, accountsPill(ids)))
                }
                applied.sentTo.takeIf { it.isNotEmpty() }?.let { sentTo ->
                    val pill = sentToPill(sentTo.map { it.address })
                    add(ActiveFacet(SENT_TO_KEY, { it.copy(sentTo = emptySet()) }, pill))
                }
                applied.receivedAs.sortedBy { it.ordinal }.forEach { field ->
                    val pill = symbolPill("As ${field.label}", Res.drawable.symbol_alternate_email)
                    add(ActiveFacet(field, { it.copy(receivedAs = it.receivedAs - field) }, pill))
                }
                applied.folders.forEach { ref ->
                    add(ActiveFacet(ref, { it.withFolder(ref, false) }, folderPill(ref)))
                }
                applied.skipFolders.forEach { ref ->
                    val pill = symbolPill("Not in ${folderName(ref).name}", Res.drawable.symbol_folder_off)
                    add(ActiveFacet("skip" to ref, { it.withSkipped(ref, false) }, pill))
                }
                applied.date.takeIf { it != DateFilter.Any }?.let { date ->
                    val label = (date as? DateFilter.Range)?.let { rangeLabel(it, dateFormatter) } ?: date.label
                    val pill = symbolPill(label, Res.drawable.symbol_calendar_add_on)
                    add(ActiveFacet(DATE_KEY, { it.copy(date = DateFilter.Any) }, pill))
                }
                applied.size.takeIf { it != SizeFilter.Any }?.let { size ->
                    val pill = symbolPill(size.label, Res.drawable.symbol_straighten)
                    add(ActiveFacet(SIZE_KEY, { it.copy(size = SizeFilter.Any) }, pill))
                }
            }

    private fun accountsPill(ids: Set<String>): ActivePill {
        val accounts = ids.mapNotNull(options::account)
        val single = accounts.singleOrNull()
        val description = single?.let { "${it.name} account" } ?: "${ids.size} accounts"
        return { AvatarFilterChip(accounts.map { it.avatar }, single?.name, description, it) }
    }

    private fun sentToPill(addresses: List<String>): ActivePill {
        val avatars =
            addresses.map { address ->
                options.addresses.firstOrNull { it.address.address == address }?.avatar
                    ?: chipAvatar(null, address, address)
            }
        val single = addresses.singleOrNull()
        val description = single?.let { "Sent to $it" } ?: "Sent to ${addresses.size} addresses"
        return { AvatarFilterChip(avatars, single, description, it) }
    }

    private fun folderPill(ref: FolderRef): ActivePill {
        val folder = options.folder(ref.key)
        val label = folderName(ref).withIncluded(ref, options)
        val icon = folder?.icon ?: Res.drawable.symbol_folder
        return { onClick ->
            Chip(label, onClick, selected = true) {
                FolderGlyph(folder?.color, icon = icon, size = PanelTokens.iconSize)
            }
        }
    }

    private fun folderName(ref: FolderRef): FolderLabel =
        options.labels[ref.key] ?: FolderLabel(options.folder(ref.key)?.name ?: "Folder")

    private companion object {
        const val FROM_KEY = "from"
        const val ACCOUNTS_KEY = "accounts"
        const val SENT_TO_KEY = "sentTo"
        const val DATE_KEY = "date"
        const val SIZE_KEY = "size"
    }
}

private fun symbolPill(
    label: String,
    icon: DrawableResource,
): ActivePill = { onClick -> Chip(label, onClick, selected = true) { Symbol(icon) } }
