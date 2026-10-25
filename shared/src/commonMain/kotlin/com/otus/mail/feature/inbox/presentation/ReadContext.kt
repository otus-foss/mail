package com.otus.mail.feature.inbox.presentation

import com.otus.designsystem.components.ToastState
import com.otus.mail.feature.inbox.domain.AttachmentDownloads
import com.otus.mail.feature.inbox.domain.ConversationMessages
import com.otus.mail.feature.inbox.domain.ConversationMute
import com.otus.mail.feature.inbox.domain.FolderChange
import com.otus.mail.feature.inbox.domain.KnownPeople
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.domain.MessageHeaders
import com.otus.mail.feature.inbox.domain.MessagePrinter
import com.otus.mail.feature.inbox.domain.Newsletters
import com.otus.mail.feature.inbox.domain.RemoteImages
import com.otus.mail.feature.inbox.domain.SavedFiles
import com.otus.mail.feature.inbox.domain.SenderBlocks
import com.otus.mail.feature.inbox.presentation.components.NewFolderSheet
import com.otus.mail.feature.inbox.presentation.components.PersonSheet
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * What the Read view needs beyond the list's actions: the conversation's [pages], the folders of a
 * message, the privacy setting for remote images, files, the
 * services behind its strips and menu, [effects] for toasts and work that outlives the page, and
 * how its pages [display]. Null in [InboxContext] keeps the screen to the message alone, as for
 * supplied messages.
 */
class ReadContext(
    val pages: ConversationMessages = ConversationMessages { null },
    val folders: ReadFolders = ReadFolders(),
    val privacy: ReadPrivacy = ReadPrivacy(),
    val files: ReadFiles = ReadFiles(),
    val services: ReadServices = ReadServices(),
    val effects: ReadEffects = ReadEffects(),
    val display: ReadDisplay = ReadDisplay(),
    /** What tapping a person in a message's details opens; null leaves the pills plain. */
    val person: PersonSheet? = null,
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
 * [apply] files every email of a conversation as a [FolderChange] says, and runs `onRefused` when
 * the server refuses it. [newFolder] shows the New folder sheet for an account until it runs its
 * `onDismiss`; null where folders cannot be made.
 */
class ReadFolders(
    val of: (MailMessage) -> List<MessageFolder> = { emptyList() },
    val cameFrom: (MailMessage) -> Set<String> = { emptySet() },
    val apply: (change: FolderChange, onRefused: () -> Unit) -> Unit = { _, _ -> },
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

/** Attachments and printing; each is null where the platform or account has none. */
class ReadFiles(
    val downloads: AttachmentDownloads? = null,
    val saved: SavedFiles? = null,
    val printer: MessagePrinter? = null,
)

/**
 * The services behind the newsletter strip and the ⋯ menu, including [changes] to one page's email;
 * each is null where none is wired.
 */
class ReadServices(
    val newsletters: Newsletters? = null,
    val headers: MessageHeaders? = null,
    val mute: ConversationMute? = null,
    val blocks: SenderBlocks? = null,
    val changes: PageChanges? = null,
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

/**
 * Changes to one email of the open conversation, apart from the rest. The Read view shows each on
 * its page at once and puts it back when a callback says the server refused it; the app keeps the
 * list's row in step.
 */
interface PageChanges {
    /** Sets [flag] to [on] on [page]'s email alone; [onRefused] runs when the server refuses it. */
    fun mark(
        row: MailMessage,
        page: MailMessage,
        flag: MessageFlag,
        on: Boolean,
        onRefused: () -> Unit,
    )

    /**
     * Moves [page]'s email alone to [target], the Trash, with an Undo toast; the conversation's row
     * stays. [onBack] runs when the email comes back, because the server refused or Undo ran.
     */
    fun trash(
        row: MailMessage,
        page: MailMessage,
        target: MoveTarget,
        onBack: () -> Unit,
    )
}
