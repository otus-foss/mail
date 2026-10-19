package com.otus.mail

import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.presentation.MoveTarget
import com.otus.mail.feature.inbox.presentation.PageChanges

/** Page changes that only record which email each one was for; [refuse] makes the server say no. */
internal class RecordedPageChanges(
    private val refuse: Boolean = false,
) : PageChanges {
    val marks = mutableListOf<Pair<String, MessageFlag>>()
    val trashed = mutableListOf<String>()
    private val backs = mutableListOf<() -> Unit>()

    override fun mark(
        row: MailMessage,
        page: MailMessage,
        flag: MessageFlag,
        on: Boolean,
        onRefused: () -> Unit,
    ) {
        marks += page.emailId to flag
        if (refuse) onRefused()
    }

    override fun trash(
        row: MailMessage,
        page: MailMessage,
        target: MoveTarget,
        onBack: () -> Unit,
    ) {
        trashed += page.emailId
        backs += onBack
    }

    /** Puts every trashed page back, as Undo does. */
    fun undo() = backs.forEach { it() }
}
