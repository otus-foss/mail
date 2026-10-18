package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageContent

/** A page's body: still loading, loaded, or failed, when the preview stays with a note. */
sealed interface PageBody {
    data object Loading : PageBody

    data class Loaded(
        val content: MessageContent,
    ) : PageBody

    data object Failed : PageBody
}

/** The newsletter strip: before unsubscribing, after it, and after archiving the older mail too. */
enum class NewsletterStep { Subscribed, Unsubscribed, Archived }

/** A sheet over the Read view: the folders of the conversation, or a page's headers. */
sealed interface ReadSheet {
    data object Folders : ReadSheet

    data class Details(
        val message: MailMessage,
    ) : ReadSheet
}

/**
 * What the Read view of [row] shows: its conversation's pages, newest first, with each page's body,
 * and which parts are open. Until the conversation loads, the row's message is the only page.
 */
@Stable
class ReadState(
    row: MailMessage,
) {
    var pages: List<MailMessage> by mutableStateOf(listOf(row))
        private set

    /** The sender details above the body. */
    var details by mutableStateOf(false)

    /** The thread strip grown into the list of messages. */
    var threadOpen by mutableStateOf(false)

    /** The attachment strip grown into its files. */
    var filesOpen by mutableStateOf(false)

    var sheet by mutableStateOf<ReadSheet?>(null)

    /** Whether the conversation is muted; null until it is known. */
    var muted by mutableStateOf<Boolean?>(null)

    private val bodies = mutableStateMapOf<String, PageBody>()
    private val imagesShown = mutableStateListOf<String>()
    private val newsletters = mutableStateMapOf<String, NewsletterStep>()

    /** Takes the conversation's pages; a failed or empty load keeps the ones shown. */
    fun showPages(found: List<MailMessage>?) {
        if (!found.isNullOrEmpty()) pages = found
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

    fun body(message: MailMessage): PageBody = bodies[message.id] ?: PageBody.Loading

    /** Loads [message]'s body once; a failure shows only after the load answered. */
    suspend fun load(
        message: MailMessage,
        loadContent: suspend (MailMessage) -> MessageContent?,
    ) {
        if (bodies[message.id] is PageBody.Loaded) return
        bodies[message.id] = loadContent(message)?.let(PageBody::Loaded) ?: PageBody.Failed
    }

    /** Remote images the user showed for this message only. */
    fun imagesShown(message: MailMessage): Boolean = message.id in imagesShown

    fun showImages(message: MailMessage) {
        if (message.id !in imagesShown) imagesShown += message.id
    }

    /** Where the newsletter strip of [message] stands. */
    fun newsletter(message: MailMessage): NewsletterStep = newsletters[message.id] ?: NewsletterStep.Subscribed

    fun setNewsletter(
        message: MailMessage,
        step: NewsletterStep,
    ) {
        newsletters[message.id] = step
    }

    /** Folds what is open, the message list first and then the details; false when nothing was. */
    fun back(): Boolean {
        when {
            threadOpen -> threadOpen = false
            details -> details = false
            else -> return false
        }
        return true
    }
}
