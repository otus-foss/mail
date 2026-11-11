package com.otusfoss.mail.feature.compose.data

import com.otusfoss.core.jmap.JmapCall
import com.otusfoss.core.jmap.JmapResponse
import com.otusfoss.core.jmap.SetMethod
import com.otusfoss.core.jmap.SetResult
import com.otusfoss.core.jmap.jmapCallOr
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.SendTime
import kotlinx.serialization.json.JsonObject

/**
 * Submits the stored [copy] and returns the submission's result. When the server's own update of
 * the copy (RFC 8621, section 7.5) fails, the copy would stay in Drafts as a draft, so the same
 * patch is sent once more. The message went either way, so a failed repair does not fail the send.
 */
internal suspend fun MailAccount.submitCopy(
    route: SendRoute,
    folder: String,
    copy: String,
    draft: Draft,
    time: SendTime,
): SetResult {
    lateinit var submitting: JmapCall<SetResult>
    val response = connection.send { submitting = call(submission(route, folder, copy, draft, time)) }
    val submitted = response[submitting]
    if (submitted.createdId(SUBMISSION) != null && response.copyUpdateFailed(submitting)) {
        jmapCallOr(Unit) {
            connection.call(SetMethod(EmailType, id, update = mapOf(copy to route.filedPatch(folder, time))))
        }
    }
    return submitted
}

/** Whether the `Email/set` the server ran after [submitting] refused the copy or failed whole. */
private fun JmapResponse.copyUpdateFailed(submitting: JmapCall<*>): Boolean {
    val update = responses(submitting).drop(1).firstOrNull() ?: return false
    return update.name == "error" || (update.arguments["notUpdated"] as? JsonObject)?.isNotEmpty() == true
}
