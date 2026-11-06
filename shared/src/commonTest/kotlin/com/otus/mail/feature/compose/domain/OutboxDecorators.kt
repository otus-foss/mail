package com.otus.mail.feature.compose.domain

import com.otus.mail.core.messages.domain.AnsweredMessage
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.DraftSender
import com.otus.mail.core.messages.domain.SendTime
import com.otus.mail.core.messages.domain.SentMessage

/** This sender, storing the copy [emailId] before it submits it. */
internal fun DraftSender.storingCopy(emailId: String): DraftSender {
    val sender = this
    return object : DraftSender {
        override suspend fun send(
            draft: Draft,
            answered: AnsweredMessage?,
            time: SendTime,
        ) = sender.send(draft, answered, time)

        override suspend fun send(
            draft: Draft,
            answered: AnsweredMessage?,
            time: SendTime,
            stored: suspend (emailId: String) -> Unit,
        ): Result<SentMessage> {
            stored(emailId)
            return sender.send(draft, answered, time)
        }
    }
}

/** This checker, recording the stored copy each lookup goes by in [copies]. */
internal fun SendChecker.recordingCopies(copies: MutableList<String?>): SendChecker {
    val checker = this
    return object : SendChecker {
        override suspend fun check(
            draft: Draft,
            answered: AnsweredMessage?,
            cancelHeld: Boolean,
        ) = checker.check(draft, answered, cancelHeld)

        override suspend fun check(
            draft: Draft,
            answered: AnsweredMessage?,
            cancelHeld: Boolean,
            storedCopy: String?,
        ): SendCheck {
            copies += storedCopy
            return checker.check(draft, answered, cancelHeld)
        }
    }
}
