package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.memberMailboxes
import com.otus.mail.feature.inbox.domain.withFlag

/**
 * A sheet over the Read view: the folders of the conversation, a new folder started from there, or
 * a page's headers.
 */
sealed interface ReadSheet {
    data object Folders : ReadSheet

    data object NewFolder : ReadSheet

    data class Details(
        val message: MailMessage,
    ) : ReadSheet
}

/**
 * What the Read view of [row] shows: its conversation's pages, newest first, with each page's body,
 * and which parts are open. Until the conversation loads, the row's email is the only page; it
 * stands for itself alone, so a change on it never reaches the rest of the conversation.
 */
@Stable
class ReadState(
    row: MailMessage,
) {
    var pages: List<MailMessage> by mutableStateOf(listOf(row.copy(conversation = null)))
        private set

    /** Whether the conversation's own pages have loaded, or failed to. */
    var loaded by mutableStateOf(false)
        private set

    /** The sender details above the body. */
    var details by mutableStateOf(false)

    /** The thread strip grown into the list of messages. */
    var threadOpen by mutableStateOf(false)

    /** The page whose attachment strip is grown into its files; null while every strip is closed. */
    private var filesOpenOn by mutableStateOf<String?>(null)

    var sheet by mutableStateOf<ReadSheet?>(null)

    /** Whether the conversation is muted; null until it is known. */
    var muted by mutableStateOf<Boolean?>(null)

    /** The page on screen, kept so a recreated screen returns to it once the pages load. */
    var shownId: String? = null

    /** The pictures of the pages' image attachments. */
    val thumbnails = PageThumbnails()

    /** Each page's body, and whether the user showed its remote images. */
    val bodies = PageBodies()

    /**
     * Takes the conversation's pages; a failed or empty load keeps the ones shown. Returns the
     * index of the page that was on screen before the screen was recreated, if it is still there.
     */
    fun showPages(found: List<MailMessage>?): Int? {
        if (!found.isNullOrEmpty()) pages = found
        loaded = true
        return pages.indexOfFirst { it.id == shownId }.takeIf { it >= 0 }
    }

    /** Each email's mailboxes by Email id: the loaded pages', else the [row]'s listed emails'. */
    fun mailboxes(row: MailMessage): Map<String, Set<String>> =
        if (loaded) {
            pages.filter { it.emailId.isNotEmpty() }.associate { it.emailId to it.location.mailboxIds }
        } else {
            row.memberMailboxes
        }

    /** Shows each page's email in the mailboxes [after] gives it, by Email id. */
    fun refile(after: Map<String, Set<String>>) {
        pages =
            pages.map { page ->
                after[page.emailId]?.let { page.copy(location = page.location.copy(mailboxIds = it)) } ?: page
            }
    }

    /** Shows [transform] of the page with [id] at once, such as a new flag. */
    fun edit(
        id: String,
        transform: (MailMessage) -> MailMessage,
    ) {
        pages = pages.map { if (it.id == id) transform(it) else it }
    }

    /** Takes the page with [id] away, such as a message deleted on its own. */
    fun drop(id: String) {
        if (pages.size > 1) pages = pages.filterNot { it.id == id }
    }

    /** Puts [page] back at [index], such as after Undo; nothing when it is still there. */
    fun restore(
        page: MailMessage,
        index: Int,
    ) {
        if (pages.none { it.id == page.id }) pages = pages.take(index) + page + pages.drop(index)
    }

    /**
     * Pages as the list's [row] settles them: its listed emails are read once the row is, and none
     * is flagged once the row is not, such as after the row was marked from the list's bar.
     */
    fun follow(row: MailMessage) {
        val listed = row.memberMailboxes.keys
        pages =
            pages.map { page ->
                val seen = !row.attributes.unread && page.emailId in listed
                val read = if (seen) page.withFlag(MessageFlag.Read, true) else page
                if (row.attributes.flagged) read else read.withFlag(MessageFlag.Flagged, false)
            }
    }

    /** Whether [page]'s attachment strip is grown; another page's strip starts closed. */
    fun filesOpen(page: MailMessage): Boolean = filesOpenOn == page.id

    fun toggleFiles(page: MailMessage) {
        filesOpenOn = if (filesOpen(page)) null else page.id
    }

    /** Folds what is open: the message list, then the files, then the details; false when nothing was. */
    fun back(): Boolean {
        when {
            threadOpen -> threadOpen = false
            filesOpenOn != null -> filesOpenOn = null
            details -> details = false
            else -> return false
        }
        return true
    }

    companion object {
        /** Keeps the details and the page on screen across a recreated screen; the pages load again. */
        fun saver(row: MailMessage): Saver<ReadState, Any> =
            Saver(
                save = { listOf(it.details, it.shownId) },
                restore = { saved ->
                    val (details, shown) = saved as List<*>
                    ReadState(row).apply {
                        this.details = details == true
                        shownId = shown as? String
                    }
                },
            )
    }
}

/** The newsletter strip: before unsubscribing, after it, and after archiving the older mail too. */
enum class NewsletterStep { Subscribed, Unsubscribed, Archived }

/** How far each message's newsletter strip got, by message id, while the app runs. */
@Stable
class NewsletterSteps {
    private val steps = mutableStateMapOf<String, NewsletterStep>()

    fun of(message: MailMessage): NewsletterStep = steps[message.id] ?: NewsletterStep.Subscribed

    fun set(
        message: MailMessage,
        step: NewsletterStep,
    ) {
        steps[message.id] = step
    }
}
