package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.designsystem.mail.quickview.components.QuickCount
import com.otus.designsystem.mail.quickview.components.QuickCounts
import com.otus.designsystem.mail.quickview.components.QuickFace
import com.otus.designsystem.mail.quickview.components.QuickFaceLook
import com.otus.designsystem.mail.quickview.components.QuickFaces
import com.otus.designsystem.mail.quickview.components.QuickPerson
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.resources.symbol_flag
import com.otus.designsystem.resources.symbol_forum
import com.otus.designsystem.resources.symbol_mark_email_unread
import com.otus.designsystem.shared.people.components.ChipAvatar
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.shownName
import com.otus.mail.core.messages.presentation.components.chipAvatar
import com.otus.mail.feature.inbox.domain.participants
import com.otus.mail.feature.inbox.presentation.ReadSheet

/**
 * Everyone in the conversation besides the user, a dot on those who wrote something unread. A face
 * turns to that person's newest message, or opens what the app offers for someone who only
 * received mail; past [THREAD_FACES] the last face opens everyone.
 */
@Composable
internal fun ReadThreadModule(room: ReadRoom) {
    val pages = room.state.pages
    val people = remember(pages, room.own) { participants(pages, room.own) }
    val shown = if (people.size > THREAD_FACES) people.take(THREAD_FACES - 1) else people
    val faces =
        shown.map { (person, _) ->
            val written = pages.indexOfFirst { it.sender.address.equals(person.address, ignoreCase = true) }
            val unread =
                pages.any {
                    it.attributes.unread &&
                        it.sender.address.equals(
                            person.address,
                            ignoreCase = true,
                        )
                }
            QuickFace(
                person.firstName,
                if (written >= 0) "${person.shownName}'s newest message" else person.shownName,
                QuickFaceLook.Person(person.chipAvatar),
                { if (written >= 0) room.turnTo(written) else room.open(ReadSheet.Person(person)) },
                unread = unread,
            )
        }
    val more =
        (people.size - shown.size).takeIf { it > 0 }?.let { left ->
            QuickFace(
                "More",
                "Everyone in this thread",
                QuickFaceLook.Person(ChipAvatar("+$left", colorIndex = 0)),
                { room.open(ReadSheet.People(people)) },
            )
        }
    QuickFaces(faces + listOfNotNull(more), slots = THREAD_FACES)
}

/** Who sent the message on screen and how much of the thread they wrote; a tap opens them. */
@Composable
internal fun ReadSenderModule(room: ReadRoom) {
    val page = room.page
    val sender = page.sender
    val pages = room.state.pages
    val wrote = pages.count { it.sender.address.equals(sender.address, ignoreCase = true) }
    QuickPerson(
        sender.chipAvatar,
        sender.shownName,
        sender.address,
        if (pages.size > 1) "Wrote $wrote of ${pages.size} messages here" else "Wrote this message",
        { room.open(ReadSheet.Person(sender)) },
    )
}

/**
 * The thread in four numbers: its messages, which open the message list, and its unread
 * messages, files, and flags, each turning to the newest message that has one.
 */
@Composable
internal fun ReadCountsModule(room: ReadRoom) {
    val pages = room.state.pages
    val turnTo = { has: (MailMessage) -> Boolean ->
        {
            pages.indexOfFirst(has).takeIf { it >= 0 }?.let(room::turnTo)
                ?: Unit
        }
    }
    QuickCounts(
        listOf(
            QuickCount("Messages", Res.drawable.symbol_forum, pages.size) { room.state.threadOpen = true },
            QuickCount(
                "Unread",
                Res.drawable.symbol_mark_email_unread,
                pages.count {
                    it.attributes.unread
                },
                turnTo { it.attributes.unread },
            ),
            QuickCount(
                "Files",
                Res.drawable.symbol_attach_file,
                pages.sumOf { it.attributes.attachments.size },
                turnTo { it.attributes.attachments.isNotEmpty() },
            ),
            QuickCount(
                "Flagged",
                Res.drawable.symbol_flag,
                pages.count {
                    it.attributes.flagged
                },
                turnTo { it.attributes.flagged },
            ),
        ),
    )
}

/** Faces the thread module shows; with more people, the last one opens them all. */
private const val THREAD_FACES = 5
