package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.designsystem.components.Recipient
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.FilterPerson
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.fromPeople
import com.otus.mail.feature.inbox.presentation.personOf
import com.otus.mail.feature.inbox.presentation.toPeople

/** The people the From and To lines match, and the chips and pills find avatars in. */
class FilterPeople(
    val senders: List<FilterPerson> = emptyList(),
    val recipients: List<FilterPerson> = emptyList(),
)

/** [messages]' senders and recipients with what [state]'s searches found, filtered by their text. */
@Composable
internal fun rememberFilterPeople(
    state: InboxFilterState,
    options: FilterOptions,
    messages: List<MailMessage>,
): FilterPeople {
    val senders = state.senders
    val recipients = state.recipients
    return remember(messages, options, senders.found, senders.text, recipients.found, recipients.text) {
        val own = options.addresses.map { it.address }
        val names = options.accounts.associate { it.id to it.name }
        FilterPeople(
            fromPeople(messages, senders.found, senders.text),
            toPeople(messages, own, names, recipients.found, recipients.text),
        )
    }
}

/** A chosen person as a chip, and the filters without them for the chip's ✕. */
internal class PickedPerson(
    val recipient: Recipient,
    val remove: (InboxFilters) -> InboxFilters,
)

/** The chosen senders by name, each with the avatar of the matching [listed] person when there is one. */
internal fun pickedSenders(
    draft: InboxFilters,
    listed: List<FilterPerson>,
): List<PickedPerson> =
    draft.from.map { key ->
        val address = key.addresses.sorted().first()
        val avatar = chipAvatar(listed.personOf(key)?.avatar, key.name, address)
        PickedPerson(Recipient(key.label, avatar, key.addresses.sorted().joinToString())) {
            it.copy(from = it.from - key)
        }
    }

/** The chosen recipients by address: the user's own with their account's avatar, then the others. */
internal fun pickedRecipients(
    draft: InboxFilters,
    options: FilterOptions,
    listed: List<FilterPerson>,
): List<PickedPerson> {
    val own =
        draft.sentTo.map { address ->
            val person = listed.firstOrNull { it.own == address }
            val avatar =
                person?.avatar?.let { chipAvatar(it, person.name, address.address) }
                    ?: options.addresses.firstOrNull { it.address == address }?.avatar
                    ?: chipAvatar(null, address.address, address.address)
            PickedPerson(Recipient(address.address, avatar, address.address)) { it.copy(sentTo = it.sentTo - address) }
        }
    val others =
        draft.to.map { address ->
            val person = listed.firstOrNull { person -> person.addresses.any { it.equals(address, ignoreCase = true) } }
            val avatar = chipAvatar(person?.avatar, person?.name.orEmpty().ifBlank { address }, address)
            PickedPerson(Recipient(address, avatar, address)) { it.copy(to = it.to - address) }
        }
    return own + others
}

/**
 * A person's line under their name in the From and To matches: their address, or the first
 * address and how many they use, and in how many conversations they appear.
 */
internal val FilterPerson.subline: String?
    get() {
        val first = addresses.first()
        val where =
            when {
                own != null -> listOf("Your address", accountName).filter(String::isNotBlank).joinToString(" · ")
                addresses.size > 1 -> "$first, ${addresses.size} addresses"
                name.isBlank() -> null
                else -> first
            }
        val seen =
            when (conversations) {
                0 -> null
                1 -> "1 conversation"
                else -> "$conversations conversations"
            }
        return listOfNotNull(where, seen).joinToString(" · ").ifEmpty { null }
    }
