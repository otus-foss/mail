package com.otus.mail.feature.inbox.data

import com.otus.mail.feature.inbox.domain.MailMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject

/**
 * Applies [patch] to each of [messages] and the emails of their conversations with `Email/set`
 * updates (RFC 8621, section 4.6), in the mail account of each message's login. Accounts are asked
 * at the same time, so a slow one holds up only its own messages. Returns the [MailMessage.id]s
 * whose listed emails all changed or wait for the server; the others were refused or have no server id.
 */
internal suspend fun EmailChangeQueue.updateRows(
    messages: List<MailMessage>,
    patch: JsonObject,
): Set<String> =
    coroutineScope {
        messages
            .filter { it.emailId.isNotEmpty() }
            .groupBy { it.location.accountId }
            .map { (loginId, group) ->
                async {
                    val updated = update(loginId, group.flatMap { it.memberIds }.associateWith { patch })
                    group.filter { updated.containsAll(it.memberIds) }.map { it.id }
                }
            }.awaitAll()
            .flatten()
            .toSet()
    }

/** [updateRows] for one message. */
internal suspend fun EmailChangeQueue.updateRow(
    message: MailMessage,
    patch: JsonObject,
): Boolean = message.id in updateRows(listOf(message), patch)

/**
 * The emails an action on this row changes: every listed email of its conversation, or only its
 * own email when it stands alone.
 */
internal val MailMessage.memberIds: List<String>
    get() = (conversation?.members?.map { it.emailId } ?: listOf(emailId)).filter { it.isNotEmpty() }.distinct()
