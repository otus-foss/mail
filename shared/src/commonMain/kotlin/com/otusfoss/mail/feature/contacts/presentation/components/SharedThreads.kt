package com.otusfoss.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.shared.people.components.PersonAvatar
import com.otusfoss.core.designsystem.shared.people.components.PersonRow
import com.otusfoss.core.designsystem.theme.tokens.people.AvatarTokens
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.SharedPerson
import com.otusfoss.mail.core.messages.domain.shownName
import com.otusfoss.mail.core.messages.presentation.components.chipAvatar

/** One row per person in [shared], with how many threads they share and the newest one's subject. */
@Composable
internal fun SharedThreads(
    shared: List<SharedPerson>,
    onPick: (MailSender) -> Unit,
) {
    shared.forEach { (person, threads, lastSubject) ->
        PersonRow(
            person.shownName,
            listOf(threadCount(threads), lastSubject).filter { it.isNotBlank() }.joinToString(" · "),
            onClick = { onPick(person) },
            modifier = RowInset,
        ) { PersonAvatar(person.chipAvatar, AvatarTokens.menuSize) }
    }
}

/** "4 people also take part", for the shared threads page's header. */
internal fun alsoTakePart(count: Int): String =
    if (count == 1) "1 person also takes part" else "$count people also take part"

private fun threadCount(count: Int): String = if (count == 1) "1 thread" else "$count threads"
