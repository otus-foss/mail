package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.otus.designsystem.components.OptionsItem
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageContent
import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.PageBody
import com.otus.mail.feature.inbox.presentation.ReadContext
import com.otus.mail.feature.inbox.presentation.ReadLaterChange
import com.otus.mail.feature.inbox.presentation.ReadSheet
import com.otus.mail.feature.inbox.presentation.ReadState
import com.otus.mail.feature.inbox.presentation.read.ReadPage
import com.otus.mail.feature.inbox.presentation.read.readMenu

/**
 * What every part of an open message's screen works from: the [row] the list shows, its
 * [actions], the list's [context] and its [read] services, the screen's [state], and the Move to
 * sheet. [perform] runs an action, closing the page first when it takes the row away.
 */
internal class ReadParts(
    val row: MailMessage,
    val actions: MessageActions?,
    val context: InboxContext,
    val read: ReadContext,
    val state: ReadState,
    val moveChoice: MoveChoice,
    private val onClose: () -> Unit,
) {
    fun perform(action: OfferedAction) {
        if (action.leaves) onClose()
        action.run()
    }

    fun close() = onClose()

    /**
     * [page] with the row's marks when it is the row's own email, since the list shows a mark at
     * once and puts it back when the server refuses it.
     */
    fun live(page: MailMessage): MailMessage {
        if (page.emailId.isEmpty() || page.emailId != row.emailId) return page
        val marks = row.attributes
        return page.copy(attributes = page.attributes.copy(unread = marks.unread, flagged = marks.flagged))
    }

    /** The loaded body of [page]; null while it loads or after it failed. */
    fun content(page: MailMessage): MessageContent? = (state.body(page) as? PageBody.Loaded)?.content
}

/** The New folder sheet for an account, which the app supplies; it shows until it runs `onDismiss`. */
fun interface NewFolderSheet {
    @Composable
    fun Show(
        accountId: String,
        onDismiss: () -> Unit,
    )
}

/** How the pages look, once the account's known people have loaded for the image rule. */
@Composable
internal fun rememberPageLook(parts: ReadParts): ReadPageLook {
    val account = parts.row.location.accountId
    val known by produceState<Set<String>?>(null, account) {
        value =
            parts.read.privacy.known
                .addresses(account)
    }
    return ReadPageLook(
        parts.read,
        parts.state,
        known,
        parts.read.folders
            .of(parts.row)
            .associateBy { it.id },
    )
}

/** The ⋯ menu for the page at [index], with what the [bar] offers beyond it at the end. */
@Composable
internal fun rememberReadMenu(
    parts: ReadParts,
    look: ReadPageLook,
    bar: ReadBar,
    index: Int,
): List<OptionsItem> {
    val row = parts.row
    val suggested by produceState<String?>(null, row.id) {
        value =
            parts.actions
                ?.moves
                ?.suggested(row)
                ?.label
    }
    val state = parts.state
    val page = parts.live(state.pages[index])
    val content = parts.content(page)
    val readPage =
        ReadPage(
            page,
            parts.context.rowLook.own,
            index,
            state.pages.size,
            newsletter = content?.unsubscribe != null,
            imagesBlocked = look.blocked(page, state.body(page)),
            muted = state.muted == true,
            suggestedPath = suggested?.let(::listOf),
        )
    val menu =
        ReadMenuActions(
            row,
            parts.actions,
            parts.read,
            state,
            parts.context.onCompose,
            parts::perform,
        ) { messages, targets ->
            parts.moveChoice.choose(messages, targets)
        }
    val entries = menu.available(readMenu(readPage))
    return readMenuItems(entries, { menu.run(it, page, content) }, bar.more, parts::perform, page.attributes.unread)
}

/** The sheets over the Read view: the conversation's folders, a new folder, and a page's headers. */
@Composable
internal fun ReadSheets(
    parts: ReadParts,
    look: ReadPageLook,
    cameFrom: String?,
) {
    val state = parts.state
    val read = parts.read
    val row = parts.row
    val dismiss = { state.sheet = null }
    when (val sheet = state.sheet ?: return) {
        ReadSheet.Folders -> {
            FoldersSheet(
                row,
                look.folders.values.toList(),
                cameFrom,
                onApply = { added, removed -> read.folders.apply(row, added, removed) },
                onNewFolder = read.folders.newFolder?.let { { state.sheet = ReadSheet.NewFolder } },
                onDismiss = dismiss,
            )
        }

        // Closing it goes back to the folders, where the new one can be ticked.
        ReadSheet.NewFolder -> {
            read.folders.newFolder?.Show(row.location.accountId) { state.sheet = ReadSheet.Folders }
        }

        is ReadSheet.Details -> {
            MessageDetailsSheet(sheet.message, { read.services.headers?.headers(sheet.message) }, dismiss)
        }
    }
}

/**
 * What the Read view's header does: Back, the sender pill that shows or hides the details, which
 * are [senderOpen], [readLater] for the bookmark, which is null while Read later is off, and the
 * ⋯ [menu].
 */
internal class ReadHeaderActions(
    val onBack: () -> Unit,
    val senderOpen: Boolean,
    val onSender: () -> Unit,
    val readLater: ReadLaterChange?,
    val menu: List<OptionsItem>,
)

/**
 * Where the Read view's strips come from: the [row] the list shows, every folder of its account by
 * id, the folder the user [cameFrom], and the page's short [date].
 */
internal class ReadStripSource(
    val row: MailMessage,
    val folders: Map<String, MessageFolder>,
    val cameFrom: String?,
    val date: String,
)
