package com.otusfoss.mail.feature.read.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otusfoss.core.designsystem.mail.quickview.components.QuickFace
import com.otusfoss.core.designsystem.mail.quickview.components.QuickFaceLook
import com.otusfoss.core.designsystem.mail.quickview.components.QuickFaces
import com.otusfoss.core.designsystem.shared.people.components.ChipAvatar
import com.otusfoss.mail.core.messages.domain.shownName
import com.otusfoss.mail.core.messages.presentation.components.chipAvatar
import com.otusfoss.mail.core.messages.presentation.components.firstName
import com.otusfoss.mail.feature.read.domain.participants
import com.otusfoss.mail.feature.read.presentation.ReadSheet

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

/** Faces the thread module shows; with more people, the last one opens them all. */
private const val THREAD_FACES = 5
