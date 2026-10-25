package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ChipAvatar
import com.otus.designsystem.components.ContactRow
import com.otus.designsystem.components.ContactRowLabel
import com.otus.designsystem.components.InfoStrip
import com.otus.designsystem.components.StripAction
import com.otus.designsystem.elements.SectionCaption
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_call_merge
import com.otus.designsystem.resources.symbol_cloud_off
import com.otus.designsystem.resources.symbol_group
import com.otus.designsystem.resources.symbol_lock
import com.otus.designsystem.resources.symbol_schedule
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.CardSync
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.domain.PossibleDuplicate
import com.otus.mail.feature.contacts.presentation.avatar
import com.otus.mail.feature.contacts.presentation.peopleCount
import com.otus.mail.feature.contacts.presentation.rowLabel

/** What the list shows: the notices on top, the groups, then the people filed by letter. */
internal class ContactListContent(
    val people: List<ContactPerson>,
    val groups: List<ContactCard>,
    val duplicate: PossibleDuplicate?,
    val duplicates: Int,
    val waiting: Int,
    val readOnly: ContactBook?,
    val booksOf: (ContactCard) -> List<ContactBook>,
    val membersOf: (ContactCard) -> Int,
)

/** What tapping and holding rows does; while [selected] is not empty a tap toggles a row. */
internal class ContactListActions(
    val selected: Set<CardKey>,
    val onOpen: (CardKey) -> Unit,
    val onToggle: (CardKey) -> Unit,
    val onReviewDuplicate: () -> Unit,
)

@Composable
internal fun ContactList(
    content: ContactListContent,
    actions: ContactListActions,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, contentPadding = padding) {
        notices(content, actions)
        if (content.groups.isNotEmpty()) {
            item("groups") { SectionCaption("Groups", Modifier.padding(top = PageTokens.rowGap)) }
            items(content.groups, key = { "group:${it.key}" }) { group ->
                val shared = content.booksOf(group).firstNotNullOfOrNull { it.rowLabel() }
                ListRow(
                    group.displayName,
                    peopleCount(content.membersOf(group)),
                    groupAvatar,
                    group.key,
                    actions,
                    label = shared,
                )
            }
        }
        content.people.groupBy { it.letter }.forEach { (letter, people) ->
            item("letter:$letter") { SectionCaption(letter, Modifier.padding(top = PageTokens.rowGap)) }
            items(people, key = { it.main.key.toString() }) { person ->
                val main = person.main
                val extra = person.emails.size - 1
                ListRow(
                    person.name,
                    person.emails.firstOrNull()?.value ?: main.role ?: "No address",
                    person.avatar,
                    main.key,
                    actions,
                    detail = "+$extra".takeIf { extra > 0 },
                    label =
                        main.sync.label()
                            ?: person.cards.flatMap(content.booksOf).firstNotNullOfOrNull { it.rowLabel() },
                )
            }
        }
    }
}

private fun LazyListScope.notices(
    content: ContactListContent,
    actions: ContactListActions,
) {
    content.readOnly?.let { book ->
        item("read-only") {
            InfoStrip(
                Res.drawable.symbol_lock,
                "${book.sharedBy ?: "Someone"} shares ${book.name} with you",
                "You can read it, not change it.",
                null,
                Modifier.padding(vertical = PageTokens.rowGap),
            )
        }
    }
    if (content.waiting > 0) {
        item("waiting") {
            val changes = if (content.waiting == 1) "1 change" else "${content.waiting} changes"
            InfoStrip(
                Res.drawable.symbol_cloud_off,
                "$changes not synced yet",
                "They sync when you’re back online.",
                null,
                Modifier.padding(vertical = PageTokens.rowGap),
            )
        }
    }
    if (content.duplicate != null) {
        item("duplicate") {
            val title =
                if (content.duplicates ==
                    1
                ) {
                    "1 possible duplicate"
                } else {
                    "${content.duplicates} possible duplicates"
                }
            InfoStrip(
                Res.drawable.symbol_group,
                title,
                null,
                StripAction("Review", Res.drawable.symbol_call_merge, actions.onReviewDuplicate),
                Modifier.padding(vertical = PageTokens.rowGap),
            )
        }
    }
}

@Composable
private fun ListRow(
    title: String,
    subtitle: String,
    avatar: ChipAvatar,
    key: CardKey,
    actions: ContactListActions,
    detail: String? = null,
    label: ContactRowLabel? = null,
) {
    val selecting = actions.selected.isNotEmpty()
    ContactRow(
        title,
        subtitle,
        avatar,
        onClick = { if (selecting) actions.onToggle(key) else actions.onOpen(key) },
        onLongClick = { actions.onToggle(key) },
        selected = key in actions.selected,
        detail = detail,
        label = label,
    )
}

private val groupAvatar = ChipAvatar("", 0, symbol = Res.drawable.symbol_group)

/** A pill for a change not on the server yet. */
private fun CardSync.label(): ContactRowLabel? =
    when (this) {
        CardSync.Synced -> null
        CardSync.Waiting -> ContactRowLabel("Not synced", Res.drawable.symbol_schedule)
        is CardSync.Refused -> ContactRowLabel("Not saved", Res.drawable.symbol_cloud_off)
    }
