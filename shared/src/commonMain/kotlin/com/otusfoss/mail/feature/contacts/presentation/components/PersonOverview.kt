package com.otusfoss.mail.feature.contacts.presentation.components

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.style.TextOverflow
import com.otusfoss.core.designsystem.mail.read.components.MailLine
import com.otusfoss.core.designsystem.mail.read.components.RecentMail
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_call
import com.otusfoss.core.designsystem.resources.symbol_mail
import com.otusfoss.core.designsystem.shared.overlays.elements.sheetInset
import com.otusfoss.core.designsystem.shared.people.components.PersonTile
import com.otusfoss.core.designsystem.shared.people.components.PersonTiles
import com.otusfoss.core.designsystem.shared.people.components.SharedPeopleRow
import com.otusfoss.core.settings.presentation.components.LocalDateFormatter
import com.otusfoss.mail.core.messages.domain.SharedPerson
import com.otusfoss.mail.core.messages.presentation.components.chipAvatar
import com.otusfoss.mail.core.messages.presentation.components.firstName
import com.otusfoss.mail.core.messages.presentation.components.mailToday
import com.otusfoss.mail.core.messages.presentation.shortLabel
import com.otusfoss.mail.feature.contacts.presentation.PersonMail

/**
 * The person's card [note], the tiles to write, call when they have a [phone], take the
 * [contactActions], such as New contact, and show their mail, the people they share threads
 * with, which [onShared] lists, and their recent threads. Each part shows only when there is
 * something in it.
 */
@Composable
internal fun PersonOverview(
    note: String?,
    phone: String?,
    mail: PersonMail,
    activity: PersonActivity?,
    contactActions: List<PersonTile.Action>,
    onShared: () -> Unit,
) {
    if (!note.isNullOrBlank()) {
        Text(note, Modifier.sheetInset(), maxLines = NOTE_LINES, overflow = TextOverflow.Ellipsis)
    }
    val uris = LocalUriHandler.current
    PersonTiles(
        buildList {
            add(PersonTile.Action("Write", Res.drawable.symbol_mail, mail.write, primary = true))
            if (phone != null) add(PersonTile.Action("Call", Res.drawable.symbol_call, { uris.call(phone) }))
            addAll(contactActions)
            val messages = activity?.messages?.takeIf { it > 0 }
            if (messages != null) {
                add(PersonTile.Count("Messages", messages) { mail.showMail(false) })
                activity.unread?.let { add(PersonTile.Count("Unread", it) { mail.showMail(true) }) }
            }
        },
        Modifier.sheetInset(),
    )
    val history = activity?.history ?: return
    if (history.shared.isNotEmpty()) {
        SharedPeopleRow(
            history.shared.take(FACES).map { it.person.chipAvatar },
            alsoInThreadsWith(history.shared),
            onShared,
            Modifier.sheetInset(),
        )
    }
    if (history.recent.isNotEmpty()) {
        val dates = LocalDateFormatter.current
        val today = mailToday()
        RecentMail(
            "Recent",
            history.recent.map {
                MailLine(
                    it.subject.ifBlank { NO_SUBJECT },
                    it.preview,
                    dates.shortLabel(it.received, today),
                    unread = it.unread,
                    current = it.threadId == mail.currentThreadId,
                )
            },
            onOpen = { mail.openThread(history.recent[it]) },
        )
    }
}

/** "Also in threads with Ben, Priya and 2 others", naming up to two people before a count. */
internal fun alsoInThreadsWith(shared: List<SharedPerson>): String {
    val names = shared.map { it.person.firstName }
    val listed =
        when {
            names.size == 1 -> names.single()
            names.size <= NAMED + 1 -> names.dropLast(1).joinToString(", ") + " and " + names.last()
            else -> names.take(NAMED).joinToString(", ") + " and ${names.size - NAMED} others"
        }
    return "Also in threads with $listed"
}

/** Hands [number] to the phone's dialer, which asks before it calls; a phone without one does nothing. */
private fun UriHandler.call(number: String) {
    try {
        openUri("tel:" + number.filter { it.isDigit() || it == '+' })
    } catch (expected: IllegalArgumentException) {
        // No app takes phone numbers here, such as on a tablet.
    }
}

private const val NOTE_LINES = 3
private const val FACES = 3
private const val NAMED = 2
private const val NO_SUBJECT = "No subject"
