package com.otus.mail.feature.inbox.presentation

import com.otus.designsystem.components.ToastState
import com.otus.mail.feature.inbox.domain.AttachmentDownloads
import com.otus.mail.feature.inbox.domain.ConversationMessages
import com.otus.mail.feature.inbox.domain.ConversationMute
import com.otus.mail.feature.inbox.domain.KnownPeople
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.domain.MessageHeaders
import com.otus.mail.feature.inbox.domain.MessagePrinter
import com.otus.mail.feature.inbox.domain.Newsletters
import com.otus.mail.feature.inbox.domain.RemoteImages
import com.otus.mail.feature.inbox.domain.SavedFiles
import com.otus.mail.feature.inbox.domain.SenderBlocks
import com.otus.mail.feature.inbox.presentation.components.NewFolderSheet
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * What the Read view needs beyond the list's actions: the conversation's [pages], the folders of a
 * message, the privacy setting for remote images, files, the services behind its strips and menu,
 * and [effects] for toasts and work that outlives the page. Null in [InboxContext] keeps the
 * screen to the message alone, as for supplied messages.
 */
class ReadContext(
    val pages: ConversationMessages = ConversationMessages { null },
    val folders: ReadFolders = ReadFolders(),
    val privacy: ReadPrivacy = ReadPrivacy(),
    val files: ReadFiles = ReadFiles(),
    val services: ReadServices = ReadServices(),
    val effects: ReadEffects = ReadEffects(),
    val today: () -> LocalDate = {
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    },
)

/**
 * The folders of a message's account as the Read view names them. [cameFrom] is the folder the
 * list shows when it shows one folder, which the strips leave out; null without a folder context.
 * [apply] adds the message, with every email of its conversation, to the added folders and takes
 * it out of the removed ones. [newFolder] shows the New folder sheet for an account until it runs
 * its `onDismiss`; null where folders cannot be made.
 */
class ReadFolders(
    val of: (MailMessage) -> List<MessageFolder> = { emptyList() },
    val cameFrom: (MailMessage) -> String? = { null },
    val apply: (message: MailMessage, added: Set<String>, removed: Set<String>) -> Unit = { _, _, _ -> },
    val newFolder: NewFolderSheet? = null,
)

/**
 * When remote images load: the saved [setting], the [allowed] senders, and the [known] people of
 * an account. [allowSender] adds an address to the allowed senders.
 */
class ReadPrivacy(
    val setting: () -> RemoteImages = { RemoteImages.Known },
    val allowed: () -> List<String> = { emptyList() },
    val known: KnownPeople = KnownPeople { emptySet() },
    val allowSender: (String) -> Unit = {},
)

/** Attachments and printing; each is null where the platform or account has none. */
class ReadFiles(
    val downloads: AttachmentDownloads? = null,
    val saved: SavedFiles? = null,
    val printer: MessagePrinter? = null,
)

/** The services behind the newsletter strip and the ⋯ menu; each is null where none is wired. */
class ReadServices(
    val newsletters: Newsletters? = null,
    val headers: MessageHeaders? = null,
    val mute: ConversationMute? = null,
    val blocks: SenderBlocks? = null,
)

/**
 * The Read view's [toasts], and [launch], which runs work in the app's scope, so an unsubscribe
 * sent once its Undo toast closes still runs after the page has closed.
 */
class ReadEffects(
    val toasts: ToastState = ToastState(),
    val launch: (suspend () -> Unit) -> Unit = {},
)
