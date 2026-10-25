package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ChipAvatar
import com.otus.designsystem.components.ContactEntry
import com.otus.designsystem.components.ContactRowLabel
import com.otus.designsystem.components.IndexLetter
import com.otus.designsystem.components.InfoStrip
import com.otus.designsystem.components.LetterIndexState
import com.otus.designsystem.components.RowSelection
import com.otus.designsystem.components.StripAction
import com.otus.designsystem.components.letterIndexHandle
import com.otus.designsystem.components.rowSelection
import com.otus.designsystem.elements.SectionCaption
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_call_merge
import com.otus.designsystem.resources.symbol_cloud_off
import com.otus.designsystem.resources.symbol_group
import com.otus.designsystem.resources.symbol_lock
import com.otus.designsystem.resources.symbol_schedule
import com.otus.designsystem.theme.EntryTokens
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.CardSync
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.domain.PossibleDuplicate
import com.otus.mail.feature.contacts.presentation.avatar
import com.otus.mail.feature.contacts.presentation.groupAvatar
import com.otus.mail.feature.contacts.presentation.peopleCount
import com.otus.mail.feature.contacts.presentation.rowLabel
import kotlinx.coroutines.launch

/**
 * What the list shows: the notices on top, the [frequent] people as faces, the groups, then the
 * people filed by letter.
 */
internal class ContactListContent(
    val people: List<ContactPerson>,
    val groups: List<ContactCard>,
    val duplicate: PossibleDuplicate?,
    val duplicates: Int,
    val waiting: Int,
    val readOnly: ContactBook?,
    val booksOf: (ContactCard) -> List<ContactBook>,
    val membersOf: (ContactCard) -> Int,
    val frequent: List<ContactPerson> = emptyList(),
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
    letterIndex: LetterIndexState,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sections = content.people.groupBy { it.letter }
    val positions = content.letterPositions(sections)
    val indexLetters = sections.map { (letter, people) -> IndexLetter(letter, people.size) }
    val jump = { letter: String -> positions[letter]?.let { scope.launch { listState.scrollToItem(it) } } }
    LazyColumn(
        modifier,
        listState,
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(EntryTokens.gap),
    ) {
        notices(content, actions)
        if (content.frequent.isNotEmpty()) {
            item("frequent") { FrequentPeople(content.frequent, actions.onOpen) }
        }
        if (content.groups.isNotEmpty()) {
            item("groups") { SectionCaption("Groups", Modifier.padding(top = PageTokens.rowGap)) }
            val groupKeys = content.groups.map { it.key }
            itemsIndexed(content.groups, key = { _, group -> "group:${group.key}" }) { index, group ->
                val shared = content.booksOf(group).firstNotNullOfOrNull { it.rowLabel() }
                ListRow(
                    group.displayName,
                    peopleCount(content.membersOf(group)),
                    group.groupAvatar,
                    group.key,
                    rowSelection(groupKeys, index, actions.selected),
                    actions,
                    label = shared,
                )
            }
        }
        sections.forEach { (letter, people) ->
            item("letter:$letter") {
                SectionCaption(
                    letter,
                    Modifier
                        .padding(top = PageTokens.rowGap)
                        .letterIndexHandle(letterIndex, letter, indexLetters) { jump(it) },
                )
            }
            val keys = people.map { it.main.key }
            itemsIndexed(people, key = { _, person -> person.main.key.toString() }) { index, person ->
                val main = person.main
                val extra = person.emails.size - 1
                ListRow(
                    person.name,
                    person.emails.firstOrNull()?.value ?: main.role ?: "No address",
                    person.avatar,
                    main.key,
                    rowSelection(keys, index, actions.selected),
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

/**
 * Where each letter's heading sits among the list's items: after the notices, the frequent
 * people, and the groups with their heading, then after each earlier letter's heading and people.
 */
private fun ContactListContent.letterPositions(sections: Map<String, List<ContactPerson>>): Map<String, Int> {
    val notices = listOfNotNull(readOnly, waiting.takeIf { it > 0 }, duplicate).size
    val first = notices + (if (frequent.isNotEmpty()) 1 else 0) + (if (groups.isNotEmpty()) groups.size + 1 else 0)
    return sections.keys.zip(sections.values.runningFold(first) { at, people -> at + 1 + people.size }).toMap()
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
    selection: RowSelection?,
    actions: ContactListActions,
    detail: String? = null,
    label: ContactRowLabel? = null,
) {
    ContactEntry(
        title,
        subtitle,
        avatar,
        onClick = { if (selection != null) actions.onToggle(key) else actions.onOpen(key) },
        onLongClick = { actions.onToggle(key) },
        selection = selection,
        detail = detail,
        label = label,
    )
}

/** A pill for a change not on the server yet. */
private fun CardSync.label(): ContactRowLabel? =
    when (this) {
        CardSync.Synced -> null
        CardSync.Waiting -> ContactRowLabel("Not synced", Res.drawable.symbol_schedule)
        is CardSync.Refused -> ContactRowLabel("Not saved", Res.drawable.symbol_cloud_off)
    }
