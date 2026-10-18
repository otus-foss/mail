package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.ReceivedAt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal fun feedMessage(id: String) =
    MailMessage(
        id,
        MailSender("Ada", "ada@example.org"),
        "Subject $id",
        "Body",
        ReceivedAt("2026-10-01", "12:00"),
        MailLocation("Inbox", "Ada"),
    )

/**
 * A source whose server loads, device reads, and next pages each wait for the test to answer them,
 * so a test decides which finishes first. Like a request already sent, each one finishes even when
 * its load is replaced, so tests answer every one.
 */
internal class GatedSource : MailSource {
    val loads = mutableListOf<CompletableDeferred<InboxSnapshot>>()
    val cachedReads = mutableListOf<CompletableDeferred<InboxSnapshot>>()
    val pages = mutableListOf<CompletableDeferred<InboxSnapshot>>()

    override suspend fun messages(scope: MailScope): InboxSnapshot = answer(loads)

    override suspend fun cached(
        scope: MailScope,
        query: MailQuery,
    ): InboxSnapshot = answer(cachedReads)

    override suspend fun more(
        scope: MailScope,
        query: MailQuery,
    ): InboxSnapshot = answer(pages)

    private suspend fun answer(into: MutableList<CompletableDeferred<InboxSnapshot>>): InboxSnapshot {
        val answer = CompletableDeferred<InboxSnapshot>()
        into += answer
        return withContext(NonCancellable) { answer.await() }
    }
}

internal fun snapshot(
    vararg ids: String,
    hasMore: Boolean = false,
) = InboxSnapshot(ids.map(::feedMessage), hasMore = hasMore)
