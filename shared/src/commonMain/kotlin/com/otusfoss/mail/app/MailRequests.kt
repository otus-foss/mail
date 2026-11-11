package com.otusfoss.mail.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.feature.inbox.presentation.OpenRequest
import com.otusfoss.mail.feature.notifications.domain.AlertedMail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What Mail is asked to show from outside the page on screen: [pages] that work outliving the
 * activity opens, and what the platform opens Mail with, such as a tapped notification's message
 * or a `mailto:` link's draft. The [App] on screen takes each request, also one made before it
 * composed, as on a cold start. One per process; [work] is the process's scope.
 */
class MailRequests internal constructor(
    work: CoroutineScope,
) {
    internal val pages = PageRequests(work)

    private val waitingMessage = MutableStateFlow<MessageRequest?>(null)

    /** The message a tapped notification asks to open or answer, until an [App] took it; see [MessageOpens]. */
    internal val message: StateFlow<MessageRequest?> = waitingMessage.asStateFlow()

    /** The Inbox row that [message] turned out to be, until the Inbox opened it. */
    internal var row: OpenRequest? by mutableStateOf(null)

    /** Opens the email [emailId] of the login [loginId], as tapping its notification asks. */
    fun openMessage(
        loginId: String,
        emailId: String,
    ) {
        waitingMessage.value = MessageRequest(AlertedMail(loginId, emailId), reply = false)
    }

    /** Starts a reply to the email [emailId] of the login [loginId], as its notification's Reply button asks. */
    fun reply(
        loginId: String,
        emailId: String,
    ) {
        waitingMessage.value = MessageRequest(AlertedMail(loginId, emailId), reply = true)
    }

    /** Starts a draft from [request], such as one a `mailto:` link filled in. */
    fun compose(request: ComposeRequest) {
        pages.showLater { MailPage.Compose(request) }
    }

    /** Marks [request] handled, unless a newer request replaced it meanwhile. */
    internal fun took(request: MessageRequest) {
        waitingMessage.compareAndSet(request, null)
    }
}

/** A notification's message to open, or with [reply] to answer. */
internal data class MessageRequest(
    val mail: AlertedMail,
    val reply: Boolean,
)
