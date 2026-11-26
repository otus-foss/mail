package com.otusfoss.mail.core.messages.data

import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.DraftException
import com.otusfoss.mail.core.messages.domain.DraftFailure
import com.otusfoss.mail.core.messages.domain.DraftRepository
import com.otusfoss.mail.core.messages.domain.SendIdentity
import kotlinx.coroutines.CompletableDeferred

/** The server's drafts for DeviceDrafts tests: it records saves and discards, and fails as asked. */
internal class FakeDraftServer : DraftRepository {
    val saves = mutableListOf<Draft>()
    val discards = mutableListOf<Pair<String, String>>()
    var failure: MailFailure? = null
    var refused: String? = null
    var gate: CompletableDeferred<Unit>? = null
    var sent = emptySet<String>()
    private var stored = 0

    override suspend fun identities(accountId: String): List<SendIdentity> = emptyList()

    override suspend fun isDraft(
        accountId: String,
        emailId: String,
    ): Result<Boolean> {
        val failed = failure ?: return Result.success(emailId !in sent)
        return Result.failure(DraftException(DraftFailure.Mail(failed)))
    }

    override suspend fun save(draft: Draft): Result<String> {
        saves += draft
        gate?.await()
        val failed = failure ?: MailFailure.NotPermitted.takeIf { draft.accountId == refused }
        if (failed != null) return Result.failure(DraftException(DraftFailure.Mail(failed)))
        return Result.success("e${++stored}")
    }

    override suspend fun discard(
        accountId: String,
        emailId: String,
    ): Result<Unit> {
        discards += accountId to emailId
        val failed = failure ?: return Result.success(Unit)
        return Result.failure(DraftException(DraftFailure.Mail(failed)))
    }
}

/** Hands [draft] to the sender as the outbox does: it names the server's copy, then stops waiting. */
internal suspend fun DeviceDrafts.handOver(draft: Draft): Draft {
    val sending = outbox.forSending(draft)
    outbox.handedOver(draft)
    return sending
}
