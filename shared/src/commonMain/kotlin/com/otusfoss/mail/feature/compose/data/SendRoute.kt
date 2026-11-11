package com.otusfoss.mail.feature.compose.data

import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.SetError
import com.otusfoss.core.jmap.SetMethod
import com.otusfoss.core.jmap.jmapPatch
import com.otusfoss.mail.core.mail.EmailAddressKey
import com.otusfoss.mail.core.mail.EmailSubmissionType
import com.otusfoss.mail.core.mail.Keywords
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.mail.toMailFailure
import com.otusfoss.mail.core.mail.utcDate
import com.otusfoss.mail.core.messages.domain.AnsweredMessage
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.SendException
import com.otusfoss.mail.core.messages.domain.SendFailure
import com.otusfoss.mail.core.messages.domain.SendTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Instant

/**
 * The identity a message is sent as, and the account's Drafts, Sent, and Scheduled folders when it
 * has them, with the folder that can become Scheduled when it has none (see [adoptableScheduled]).
 */
internal data class SendRoute(
    val identityId: String,
    val drafts: String?,
    val sent: String?,
    val scheduled: String? = null,
    val adoptable: String? = null,
) {
    /**
     * The patch that files the sent copy, stored in [folder]: it leaves Drafts for Sent and loses
     * `$draft`. Without a Sent folder the copy stays where it is.
     */
    fun sentPatch(folder: String): JsonObject =
        jmapPatch {
            if (sent != null && sent != folder) {
                remove("mailboxIds", folder)
                set(JsonPrimitive(true), "mailboxIds", sent)
            }
            remove("keywords", Keywords.DRAFT)
        }
}

/** The patch for a held copy: it stays where it was stored and only loses `$draft`. */
internal fun SendRoute.heldPatch(): JsonObject = jmapPatch { remove("keywords", Keywords.DRAFT) }

/**
 * The envelope that holds a draft until [at] (RFC 4865 HOLDUNTIL), from its From address to every
 * recipient. An explicit envelope is needed to carry the parameter.
 */
internal fun Draft.heldEnvelope(at: Instant): JsonObject =
    buildJsonObject {
        putJsonObject("mailFrom") {
            put("email", from?.address.orEmpty())
            putJsonObject("parameters") { put("HOLDUNTIL", at.utcDate()) }
        }
        putJsonArray("rcptTo") {
            (to + cc + bcc)
                .distinctBy {
                    EmailAddressKey(
                        it.address,
                    )
                }.forEach { addJsonObject { put("email", it.address) } }
        }
    }

internal val AnsweredMessage.keyword: String
    get() = if (forwarded) Keywords.FORWARDED else Keywords.ANSWERED

internal fun failSend(failure: SendFailure): Nothing = throw SendException(failure)

/** Error types from RFC 8621, section 7.5, and the standard ones from RFC 8620, section 5.3. */
internal fun SetError.toSendFailure(): SendFailure = sendFailure(type)

internal fun JmapException.toSendFailure(): SendFailure =
    if (this is JmapException.MethodError) {
        sendFailure(type)
    } else {
        // The outbox takes any other failure as one that may have sent, so the rest stay server problems.
        SendFailure.Mail(
            toMailFailure().takeIf { it == MailFailure.NotPermitted || it == MailFailure.Unreachable }
                ?: MailFailure.ServerProblem,
        )
    }

private fun sendFailure(type: String): SendFailure =
    when (type) {
        "noRecipients", "invalidRecipients" -> SendFailure.Recipients
        "tooManyRecipients" -> SendFailure.TooManyRecipients
        "tooLarge" -> SendFailure.TooLarge
        "cannotUnsend" -> SendFailure.CannotUnsend
        in FORBIDDEN -> SendFailure.Mail(MailFailure.NotPermitted)
        else -> SendFailure.Mail(MailFailure.ServerProblem)
    }

private val FORBIDDEN = setOf("forbidden", "forbiddenFrom", "forbiddenMailFrom", "forbiddenToSend")

/**
 * The submission of the stored [copy] in [folder]. A held or scheduled one carries an envelope
 * with its time. A scheduled copy stays in [folder], only losing `$draft`, since the server
 * sends it much later, and Mail moves it to Sent once it went; any other moves at once.
 */
internal fun MailAccount.submission(
    route: SendRoute,
    folder: String,
    copy: String,
    draft: Draft,
    time: SendTime,
) = SetMethod(
    EmailSubmissionType,
    id,
    create =
        mapOf(
            SUBMISSION to
                buildJsonObject {
                    put("emailId", copy)
                    put("identityId", route.identityId)
                    when (time) {
                        SendTime.Now -> Unit
                        is SendTime.Held -> put("envelope", draft.heldEnvelope(time.at))
                        is SendTime.Scheduled -> put("envelope", draft.heldEnvelope(time.at))
                    }
                },
        ),
    extra =
        mapOf(
            "onSuccessUpdateEmail" to
                buildJsonObject {
                    put(
                        "#$SUBMISSION",
                        if (time is SendTime.Scheduled) route.heldPatch() else route.sentPatch(folder),
                    )
                },
        ),
)

/**
 * This email dated [at], when a scheduled send goes: its arrival in the account and its Date
 * header, so lists show and sort it by that time and recipients see it too.
 */
internal fun JsonObject.sendingAt(at: Instant): JsonObject =
    JsonObject(this + mapOf("receivedAt" to JsonPrimitive(at.utcDate()), "sentAt" to JsonPrimitive(at.utcDate())))

/** The creation id of a send's submission. */
internal const val SUBMISSION = "send"
