package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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

/** The Read view's page bodies by message id, and the pages whose remote images the user showed. */
@Stable
class PageBodies {
    private val bodies = mutableStateMapOf<String, PageBody>()
    private val imagesShown = mutableStateListOf<String>()

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
}
