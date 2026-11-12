package com.otusfoss.mail.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.OwnAddresses
import com.otusfoss.mail.core.messages.domain.ReadStatus
import com.otusfoss.mail.core.messages.domain.SenderKey
import com.otusfoss.mail.core.messages.presentation.components.chipAvatar
import com.otusfoss.mail.feature.contacts.domain.ContactAddress
import com.otusfoss.mail.feature.contacts.presentation.PersonMail
import com.otusfoss.mail.feature.contacts.presentation.components.PersonContactSheet
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.presentation.InboxFilterState
import com.otusfoss.mail.feature.read.presentation.components.PersonSheet

/**
 * The Read view's sheet for a person a message names, on [page] whose list [filters] apply; [own]
 * addresses stay out of the people it lists. Its counts are the list's own counts of mail from
 * them, and its tiles show that mail in the list.
 */
internal fun personSheet(
    states: MailStates,
    filters: InboxFilterState,
    navigation: MailNavigation,
    page: MailPage,
    own: OwnAddresses,
): PersonSheet =
    PersonSheet { person, message, closeRead, onDismiss ->
        val contacts = states.contacts.cards
        LaunchedEffect(Unit) { if (contacts.snapshot == null) contacts.refresh() }
        val accountId = message.location.accountId
        val leave = { action: () -> Unit ->
            onDismiss()
            closeRead()
            action()
        }
        val mail =
            remember(person, message) {
                val from = setOf(SenderKey(person.name, setOf(person.address)))
                val theirs = { unread: Boolean ->
                    InboxFilters(status = if (unread) ReadStatus.Unread else ReadStatus.All, from = from)
                }
                PersonMail(
                    count = { unread -> filters.countOf(theirs(unread)) },
                    history = {
                        states.reading.personHistories.of(accountId, person.address) { own.isMine(accountId, it) }
                    },
                    currentThreadId = message.conversation?.threadId,
                    write = {
                        onDismiss()
                        navigation.go(
                            MailPage.Compose(ComposeRequest.New(accountId, to = listOf(person)), parent = page),
                        )
                    },
                    showMail = { unread -> leave { filters.update(theirs(unread)) } },
                    showWith = { other: MailSender ->
                        leave { filters.update(InboxFilters(from = from, to = setOf(other.address))) }
                    },
                    openThread = { thread -> leave { states.reading.openMessage(accountId, thread.emailId) } },
                )
            }
        PersonContactSheet(
            ContactAddress(person.name, person.address),
            person.chipAvatar,
            contacts,
            mail,
            onNew = {
                onDismiss()
                navigation.go(MailPage.ContactEditor())
            },
            onOpen = {
                onDismiss()
                navigation.go(MailPage.Contact(it))
            },
            onDismiss = onDismiss,
        )
    }
