package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.Participant
import com.otus.mail.feature.inbox.domain.memberMailboxes

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

    /** What the app offers for a [person] the message names, such as adding them to contacts. */
    data class Person(
        val person: MailSender,
    ) : ReadSheet

    /** Everyone a conversation names besides the user, each opening their [Person] sheet. */
    data class People(
        val people: List<Participant>,
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
    var filesOpenOn by mutableStateOf<String?>(null)

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
     * Takes the conversation's pages, with the [edits] their load may have missed; a failed or
     * empty load keeps the ones shown. Returns the index of the page that was on screen before the
     * screen was recreated, if it is still there.
     */
    fun showPages(
        found: List<MailMessage>?,
        edits: List<MailEdit> = emptyList(),
    ): Int? {
        if (!found.isNullOrEmpty()) pages = found
        edits.forEach(::apply)
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

    /**
     * Shows each edit of the pages' emails at once, wherever it was made, such as Mark read from
     * the list's bar; a rolled back or undone edit puts the pages back as they were.
     */
    internal val edits: EditObserver =
        object : EditObserver {
            override fun shown(edit: MailEdit) = apply(edit)

            override fun settled(
                edit: MailEdit,
                kept: Boolean,
            ) {
                if (!kept) restore(edit)
            }

            override fun reverted(edit: MailEdit) = restore(edit)
        }

    private val before = mutableMapOf<MailEdit, List<IndexedValue<MailMessage>>>()

    /** The view keeps at least one page, so an edit never drops the last. */
    private fun apply(edit: MailEdit) {
        val change = edit.page ?: return
        val hit = pages.withIndex().filter { it.value.emailId in edit.emailIds }
        if (hit.isEmpty()) return
        before[edit] = hit
        pages = pages.mapNotNull { if (it.emailId in edit.emailIds) change(it) else it }.ifEmpty { pages }
    }

    private fun restore(edit: MailEdit) {
        val old = before.remove(edit) ?: return
        pages =
            old.fold(pages) { list, (index, page) ->
                if (list.any { it.id == page.id }) {
                    list.map { if (it.id == page.id) page else it }
                } else {
                    list.take(index) + page + list.drop(index)
                }
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
