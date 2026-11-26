package com.otusfoss.mail.testing

import com.otusfoss.mail.core.messages.domain.HeldSend
import com.otusfoss.mail.core.messages.domain.ScheduledSends
import com.otusfoss.mail.core.messages.domain.SendCancel
import com.otusfoss.mail.core.messages.domain.SendException
import com.otusfoss.mail.core.messages.domain.SendFailure
import com.otusfoss.mail.core.messages.domain.SentMessage

/** Held sends that answer as told and record what was asked of them. */
internal class FakeScheduledSends : ScheduledSends {
    var maxDelay = 0L

    /** What cancel ends in; the send counts as canceled only when it stopped. */
    var cancelOutcome: SendCancel = SendCancel.Stopped
    var sendNowFailure: SendFailure? = null
    val cancelTries = mutableListOf<SentMessage>()
    val cancelled = mutableListOf<SentMessage>()
    val sentNow = mutableListOf<SentMessage>()
    val filedSent = mutableListOf<String>()

    override suspend fun maxDelaySeconds(accountId: String) = maxDelay

    override suspend fun cancel(message: SentMessage): SendCancel {
        cancelTries += message
        if (cancelOutcome == SendCancel.Stopped) cancelled += message
        return cancelOutcome
    }

    override suspend fun sendNow(message: SentMessage): Result<Unit> {
        sentNow += message
        return sendNowFailure?.let { Result.failure(SendException(it)) } ?: Result.success(Unit)
    }

    override suspend fun fileSent(accountId: String) {
        filedSent += accountId
    }

    override suspend fun held(
        accountId: String,
        emailId: String,
    ): HeldSend? = null
}
