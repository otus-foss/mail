package com.otusfoss.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import com.otusfoss.mail.core.messages.domain.PersonHistory
import com.otusfoss.mail.feature.contacts.presentation.PersonMail
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** The person's mail as the sheet loads it once it opens; each part null while unknown. */
internal class PersonActivity(
    val messages: Long?,
    val unread: Long?,
    val history: PersonHistory?,
)

/** Loads [mail]'s counts and history side by side; null until all of them answer. */
@Composable
internal fun rememberPersonActivity(mail: PersonMail): PersonActivity? =
    produceState<PersonActivity?>(null, mail) {
        value =
            coroutineScope {
                val messages = async { mail.count(false) }
                val unread = async { mail.count(true) }
                val history = async { mail.history() }
                PersonActivity(messages.await(), unread.await(), history.await())
            }
    }.value
