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
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_cloud_off
import com.otus.designsystem.resources.symbol_schedule
import com.otus.designsystem.shared.contacts.components.ContactEntry
import com.otus.designsystem.shared.contacts.components.ContactEntryEnd
import com.otus.designsystem.shared.contacts.components.ContactRowLabel
import com.otus.designsystem.shared.contacts.components.IndexLetter
import com.otus.designsystem.shared.contacts.components.LetterIndexState
import com.otus.designsystem.shared.contacts.components.letterIndexHandle
import com.otus.designsystem.shared.people.components.ChipAvatar
import com.otus.designsystem.shared.rows.components.EntryTaps
import com.otus.designsystem.shared.rows.components.RowSelection
import com.otus.designsystem.shared.rows.components.rowSelection
import com.otus.designsystem.shared.rows.elements.SectionCaption
import com.otus.designsystem.theme.EntryTokens
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.CardSync
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.presentation.AvatarLabel
import com.otus.mail.feature.contacts.presentation.avatar
import com.otus.mail.feature.contacts.presentation.groupAvatar
import com.otus.mail.feature.contacts.presentation.isOwnAvatar
import com.otus.mail.feature.contacts.presentation.peopleCount
import com.otus.mail.feature.contacts.presentation.rowLabel
import kotlinx.coroutines.launch

/**
 * What the list shows: the [notices] on top, the [frequent] people as faces, the groups, then the
 * people filed by letter.
 */
internal class ContactListContent(
    val people: List<ContactPerson>,
    val groups: List<ContactCard>,
    val notices: ListNotices,
    val booksOf: (ContactCard) -> List<ContactBook>,
    val membersOf: (ContactCard) -> Int,
    val frequent: List<ContactPerson> = emptyList(),
    /** The user's own addresses, lowercase, whose avatar cards get a You pill. */
    val ownAddresses: Set<String> = emptySet(),
)

/** What tapping and holding rows does; while [selected] is not empty a tap toggles a row. */
internal class ContactListActions(
    val selected: Set<CardKey>,
    val onOpen: (CardKey) -> Unit,
    val onToggle: (CardKey) -> Unit,
    val onReviewDuplicate: () -> Unit,
)

/** The scrolling contact list, with a letter index that jumps to each letter's heading. */
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
    val jump = { letter: String -> positions[letter]?.let { scope.launch { listState.scrollToItem(it) } } }
    LazyColumn(
        modifier,
        listState,
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(EntryTokens.gap),
    ) {
        notices(content.notices, actions.onReviewDuplicate)
        if (content.frequent.isNotEmpty()) {
            item("frequent") { FrequentPeople(content.frequent, actions.onOpen) }
        }
        groupRows(content, actions)
        letterSections(sections, content, actions, letterIndex) { jump(it) }
    }
}

/** The Groups heading and a row per group, each with its member count. */
private fun LazyListScope.groupRows(
    content: ContactListContent,
    actions: ContactListActions,
) {
    if (content.groups.isEmpty()) return
    item("groups") { SectionCaption("Groups", Modifier.padding(top = PageTokens.rowGap)) }
    val groupKeys = content.groups.map { it.key }
    itemsIndexed(content.groups, key = { _, group -> "group:${group.key}" }) { index, group ->
        ListRow(
            group.displayName,
            peopleCount(content.membersOf(group)),
            group.groupAvatar,
            group.key,
            rowSelection(groupKeys, index, actions.selected),
            actions,
            ContactEntryEnd(label = content.booksOf(group).firstNotNullOfOrNull { it.rowLabel() }),
        )
    }
}

/** A heading per letter, which the letter index can drag to, then that letter's people. */
private fun LazyListScope.letterSections(
    sections: Map<String, List<ContactPerson>>,
    content: ContactListContent,
    actions: ContactListActions,
    letterIndex: LetterIndexState,
    onJump: (String) -> Unit,
) {
    val indexLetters = sections.map { (letter, people) -> IndexLetter(letter, people.size) }
    sections.forEach { (letter, people) ->
        item("letter:$letter") {
            SectionCaption(
                letter,
                Modifier
                    .padding(top = PageTokens.rowGap)
                    .letterIndexHandle(letterIndex, letter, indexLetters, onJump),
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
                ContactEntryEnd(
                    detail = "+$extra".takeIf { extra > 0 },
                    label =
                        main.sync.label()
                            ?: AvatarLabel.takeIf { person.isOwnAvatar(content.ownAddresses) }
                            ?: person.cards.flatMap(content.booksOf).firstNotNullOfOrNull { it.rowLabel() },
                ),
            )
        }
    }
}

/**
 * Where each letter's heading sits among the list's items: after the notices, the frequent
 * people, and the groups with their heading, then after each earlier letter's heading and people.
 */
private fun ContactListContent.letterPositions(sections: Map<String, List<ContactPerson>>): Map<String, Int> {
    val first =
        notices.count + (if (frequent.isNotEmpty()) 1 else 0) + (if (groups.isNotEmpty()) groups.size + 1 else 0)
    return sections.keys.zip(sections.values.runningFold(first) { at, people -> at + 1 + people.size }).toMap()
}

/** One group or person row: a tap opens it, or toggles it while a selection is on; a hold toggles it. */
@Composable
private fun ListRow(
    title: String,
    subtitle: String,
    avatar: ChipAvatar,
    key: CardKey,
    selection: RowSelection?,
    actions: ContactListActions,
    end: ContactEntryEnd,
) {
    ContactEntry(
        title,
        subtitle,
        avatar,
        EntryTaps({ if (selection != null) actions.onToggle(key) else actions.onOpen(key) }, { actions.onToggle(key) }),
        selection = selection,
        end = end,
    )
}

/** A pill for a change not on the server yet. */
private fun CardSync.label(): ContactRowLabel? =
    when (this) {
        CardSync.Synced -> null
        CardSync.Waiting -> ContactRowLabel("Not synced", Res.drawable.symbol_schedule)
        is CardSync.Refused -> ContactRowLabel("Not saved", Res.drawable.symbol_cloud_off)
    }
