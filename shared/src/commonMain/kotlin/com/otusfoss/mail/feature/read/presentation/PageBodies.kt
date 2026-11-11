package com.otusfoss.mail.feature.read.presentation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MessageContent
import kotlinx.coroutines.flow.Flow

/** A page's body: still loading, loaded, or failed, when the preview stays with a note. */
sealed interface PageBody {
    data object Loading : PageBody

    data class Loaded(
        val content: MessageContent,
    ) : PageBody

    data object Failed : PageBody
}

/**
 * The Read view's page bodies by message id, the pages whose remote images the user showed, and
 * those whose images banner the user swiped away.
 */
@Stable
class PageBodies {
    private val bodies = mutableStateMapOf<String, PageBody>()
    private val complete = mutableSetOf<String>()
    private val imagesShown = mutableStateListOf<String>()
    private val bannersDismissed = mutableStateListOf<String>()

    fun body(message: MailMessage): PageBody = bodies[message.id] ?: PageBody.Loading

    /**
     * Loads [message]'s body once, showing each version [loadContent] gives, such as the text before
     * its inline images; a failure shows only after the load answered. A load cut short runs again.
     */
    suspend fun load(
        message: MailMessage,
        loadContent: (MailMessage) -> Flow<MessageContent?>,
    ) {
        if (message.id in complete) return
        loadContent(message).collect { bodies[message.id] = it?.let(PageBody::Loaded) ?: PageBody.Failed }
        if (bodies[message.id] is PageBody.Loaded) complete += message.id
    }

    /** Remote images the user showed for this message only. */
    fun imagesShown(message: MailMessage): Boolean = message.id in imagesShown

    fun showImages(message: MailMessage) {
        if (message.id !in imagesShown) imagesShown += message.id
    }

    /** Blocks [message]'s remote images again after the user showed them. */
    fun hideImages(message: MailMessage) {
        imagesShown -= message.id
    }

    /** Whether the user swiped away [message]'s images banner. */
    fun bannerDismissed(message: MailMessage): Boolean = message.id in bannersDismissed

    fun dismissBanner(message: MailMessage) {
        if (message.id !in bannersDismissed) bannersDismissed += message.id
    }
}
