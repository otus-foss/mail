package com.otus.mail.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.presentation.OpenRequest
import com.otus.mail.feature.notifications.domain.AlertedMail
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

    private val waitingMessage = MutableStateFlow<AlertedMail?>(null)

    /** The message a tapped notification asks to open, until an [App] took it; see [MessageOpens]. */
    internal val message: StateFlow<AlertedMail?> = waitingMessage.asStateFlow()

    /** The Inbox row that [message] turned out to be, until the Inbox opened it. */
    internal var row: OpenRequest? by mutableStateOf(null)

    /** Opens the email [emailId] of the login [loginId], as tapping its notification asks. */
    fun openMessage(
        loginId: String,
        emailId: String,
    ) {
        waitingMessage.value = AlertedMail(loginId, emailId)
    }

    /** Starts a draft from [request], such as one a `mailto:` link filled in. */
    fun compose(request: ComposeRequest) {
        pages.showLater(MailPage.Compose(request))
    }

    /** Marks [mail] handled, unless a newer request replaced it meanwhile. */
    internal fun took(mail: AlertedMail) {
        waitingMessage.compareAndSet(mail, null)
    }
}
