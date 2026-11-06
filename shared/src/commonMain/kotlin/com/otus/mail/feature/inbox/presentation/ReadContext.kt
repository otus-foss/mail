package com.otus.mail.feature.inbox.presentation

import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.mail.core.files.SavedFiles
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MessageFolder
import com.otus.mail.core.messages.domain.RemoteImages
import com.otus.mail.core.messages.domain.SenderBlocks
import com.otus.mail.core.messages.presentation.empty
import com.otus.mail.feature.inbox.domain.ConversationMessages
import com.otus.mail.feature.inbox.domain.KnownPeople
import com.otus.mail.feature.inbox.domain.MessageHeaders
import com.otus.mail.feature.inbox.domain.MessagePrinter
import com.otus.mail.feature.inbox.domain.Newsletters
import com.otus.mail.feature.inbox.domain.OfflineAttachments
import com.otus.mail.feature.inbox.presentation.components.NewFolderSheet
import com.otus.mail.feature.inbox.presentation.components.PersonSheet
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * What the Read view needs beyond the list's actions: the conversation's [pages], the folders of a
 * message, the privacy setting for remote images, files, the services behind its strips and menu,
 * [effects] for toasts and work that outlives the page, and how its pages [display]. Null in
 * [InboxContext] keeps the screen to the message alone, as for supplied messages.
 */
class ReadContext(
    val pages: ConversationMessages = ConversationMessages { null },
    val folders: ReadFolders = ReadFolders(),
    val privacy: ReadPrivacy = ReadPrivacy(),
    val files: ReadFiles = ReadFiles(),
    val services: ReadServices = ReadServices(),
    val effects: ReadEffects = ReadEffects(),
    val display: ReadDisplay = ReadDisplay(),
)

/**
 * How the Read view shows its pages: [showQuotes] tells whether replies show text quoted from
 * earlier messages of the conversation, [zen] whether the view keeps to the message, its control
 * panel, and its bar, which [setZen] saves, and [today] is the day dates are shown against.
 */
class ReadDisplay(
    val showQuotes: () -> Boolean = { false },
    val zen: () -> Boolean = { false },
    val setZen: (Boolean) -> Unit = {},
    val today: () -> LocalDate = {
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    },
)

/**
 * The folders of a message's account as the Read view names them. [cameFrom] is the folders the
 * list shows the message from, which the strips leave out; empty without a folder context.
 * [newFolder] shows the New folder sheet for an account until it runs its `onDismiss`; null where
 * folders cannot be made.
 */
class ReadFolders(
    val of: (MailMessage) -> List<MessageFolder> = { emptyList() },
    val cameFrom: (MailMessage) -> Set<String> = { emptySet() },
    val newFolder: NewFolderSheet? = null,
)

/**
 * When remote images load: the saved [setting], the [allowed] senders, and the [known] people of
 * an account. [allowSender] adds an address to the allowed senders.
 */
class ReadPrivacy(
    val setting: () -> RemoteImages = { RemoteImages.Always },
    val allowed: () -> List<String> = { emptyList() },
    val known: KnownPeople = KnownPeople { emptySet() },
    val allowSender: (String) -> Unit = {},
)

/**
 * Attachments and printing: [downloads] brings files to the device and keeps them offline, [saved]
 * opens and saves them. Each is null where the platform or account has none.
 */
class ReadFiles(
    val downloads: OfflineAttachments? = null,
    val saved: SavedFiles? = null,
    val printer: MessagePrinter? = null,
)

/**
 * The services behind the newsletter strip and the ⋯ menu, including the [edits] the view makes,
 * and [person], what tapping a person or the menu's contact entries open; each is null where none
 * is wired, and without [person] the pills stay plain.
 */
class ReadServices(
    val newsletters: Newsletters? = null,
    val headers: MessageHeaders? = null,
    val blocks: SenderBlocks? = null,
    val edits: ReadEdits? = null,
    val person: PersonSheet? = null,
)

/**
 * The Read view's [toasts], and [launch], which runs work in the process's scope, so an
 * unsubscribe sent once its Undo toast closes still runs after the page, or the activity, has
 * closed. [newsletters] remembers each newsletter strip's step, so a reopened message shows it.
 */
class ReadEffects(
    val toasts: ToastState = ToastState(),
    val launch: (suspend () -> Unit) -> Unit = {},
    val newsletters: NewsletterSteps = NewsletterSteps(),
)
