package com.otus.mail.feature.read.presentation

import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.mail.core.files.SavedFiles
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MessageContent
import com.otus.mail.core.messages.domain.MessageFolder
import com.otus.mail.core.messages.domain.OwnAddresses
import com.otus.mail.core.messages.domain.RemoteImages
import com.otus.mail.core.messages.domain.SenderBlocks
import com.otus.mail.core.messages.presentation.empty
import com.otus.mail.core.settings.domain.ReadQuickModule
import com.otus.mail.feature.read.domain.ConversationMessages
import com.otus.mail.feature.read.domain.KnownPeople
import com.otus.mail.feature.read.domain.MessageHeaders
import com.otus.mail.feature.read.domain.MessagePrinter
import com.otus.mail.feature.read.domain.Newsletters
import com.otus.mail.feature.read.domain.OfflineAttachments
import com.otus.mail.feature.read.presentation.components.NewFolderSheet
import com.otus.mail.feature.read.presentation.components.PersonSheet
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * What the Read view needs beyond the list's actions: the conversation's [pages], the folders of a
 * message, the privacy setting for remote images, files, the services behind its strips and menu,
 * [effects] for toasts and work that outlives the page, and how its pages [display]. [own] are
 * the user's addresses, [loadContent] loads a page's full body, and [onCompose] opens a draft for
 * a reply or forward.
 */
class ReadContext(
    val pages: ConversationMessages,
    val folders: ReadFolders,
    val privacy: ReadPrivacy,
    val files: ReadFiles,
    val services: ReadServices,
    val effects: ReadEffects,
    val display: ReadDisplay,
    val own: OwnAddresses,
    val loadContent: suspend (MailMessage) -> MessageContent?,
    val onCompose: (ComposeRequest) -> Unit,
)

/**
 * How the Read view shows its pages: [showQuotes] tells whether replies show text quoted from
 * earlier messages of the conversation, [zen] whether the view keeps to the message, its control
 * panel, and its bar, which [setZen] saves, [quickView] the modules its control panel shows when
 * pulled down, and [today] is the day dates are shown against.
 */
class ReadDisplay(
    val showQuotes: () -> Boolean,
    val zen: () -> Boolean,
    val quickView: () -> List<ReadQuickModule>,
    val setZen: (Boolean) -> Unit,
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
    val of: (MailMessage) -> List<MessageFolder>,
    val cameFrom: (MailMessage) -> Set<String>,
    val newFolder: NewFolderSheet?,
)

/**
 * When remote images load: the saved [setting], the [allowed] senders, and the [known] people of
 * an account. [allowSender] adds an address to the allowed senders.
 */
class ReadPrivacy(
    val setting: () -> RemoteImages,
    val allowed: () -> List<String>,
    val known: KnownPeople,
    val allowSender: (String) -> Unit,
)

/**
 * Attachments and printing: [downloads] brings files to the device and keeps them offline, [saved]
 * opens and saves them. Each is null where the platform or account has none.
 */
class ReadFiles(
    val downloads: OfflineAttachments?,
    val saved: SavedFiles?,
    val printer: MessagePrinter?,
)

/**
 * The services behind the newsletter strip and the ⋯ menu, including the [edits] the view makes,
 * and [person], what tapping a person or the menu's contact entries open; each is null where none
 * is wired, and without [person] the pills stay plain.
 */
class ReadServices(
    val newsletters: Newsletters?,
    val headers: MessageHeaders?,
    val blocks: SenderBlocks?,
    val edits: ReadEdits?,
    val person: PersonSheet?,
)

/**
 * The Read view's [toasts], and [launch], which runs work in the process's scope, so an
 * unsubscribe sent once its Undo toast closes still runs after the page, or the activity, has
 * closed. [newsletters] remembers each newsletter strip's step, so a reopened message shows it.
 */
class ReadEffects(
    val toasts: ToastState,
    val launch: (suspend () -> Unit) -> Unit,
    val newsletters: NewsletterSteps,
)
