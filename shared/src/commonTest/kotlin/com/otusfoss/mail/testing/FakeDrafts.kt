package com.otusfoss.mail.testing

import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.DraftException
import com.otusfoss.mail.core.messages.domain.DraftFailure
import com.otusfoss.mail.core.messages.domain.DraftRepository
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.SendIdentity
import kotlinx.coroutines.Deferred

/** A Drafts folder in memory; stored copies get the ids `d1`, `d2`, and so on. */
internal class FakeDrafts(
    val identities: List<MailSender> = emptyList(),
    /** Holds back the answer to a save, after the copy is stored, until it completes. */
    private val answer: Deferred<Unit>? = null,
) : DraftRepository {
    val saved = mutableListOf<Draft>()
    val discarded = mutableListOf<String>()
    var failure: DraftFailure? = null

    override suspend fun identities(accountId: String) =
        identities.filter { it.accountKey == accountId }.map { SendIdentity(it.address, it) }

    override suspend fun save(draft: Draft): Result<String> {
        failure?.let { return Result.failure(DraftException(it)) }
        saved += draft
        answer?.await()
        return Result.success("d${saved.size}")
    }

    override suspend fun discard(
        accountId: String,
        emailId: String,
    ): Result<Unit> {
        discarded += emailId
        return Result.success(Unit)
    }
}
