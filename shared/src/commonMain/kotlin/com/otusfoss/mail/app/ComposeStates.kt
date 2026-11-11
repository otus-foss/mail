package com.otusfoss.mail.app

import com.otusfoss.core.accounts.domain.ownAddresses
import com.otusfoss.core.accounts.presentation.AccountsState
import com.otusfoss.core.avatar.ui.presentation.OwnAvatarsState
import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.HeldSend
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.accountId
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.feature.compose.presentation.ComposeAccount
import com.otusfoss.mail.feature.compose.presentation.ComposeOrigin
import com.otusfoss.mail.feature.compose.presentation.ComposeServices
import com.otusfoss.mail.feature.compose.presentation.ComposeState
import com.otusfoss.mail.feature.compose.presentation.HeldEdits
import com.otusfoss.mail.feature.compose.presentation.OutgoingMail
import com.otusfoss.mail.feature.compose.presentation.ScheduledSendNow
import com.otusfoss.mail.feature.compose.presentation.SignaturesState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Drafts from the user's [accounts], every address's signature, and the mail that is sending.
 * One per [MailStates].
 */
internal class ComposeStates(
    private val services: MailServices,
    scope: CoroutineScope,
    private val behavior: MailBehaviorState,
    private val accounts: AccountsState,
    private val ownAvatars: OwnAvatarsState,
    dates: () -> DateFormatter,
) {
    /**
     * A draft for [request] from the account it names, or else the first one; null without
     * accounts. Every account can send it. Reply all leaves out the account's sign-in and sending
     * addresses. Sending records the address it went from, which later drafts offer first.
     */
    fun draft(request: ComposeRequest): ComposeState? {
        val all = accounts.accounts
        val account = all.firstOrNull { it.id == request.accountId } ?: all.firstOrNull() ?: return null
        return ComposeState(
            ComposeServices(
                services.drafts,
                services.addressBook,
                services.scheduled,
                services.threads,
                services.uploads,
                services.pickers.attachmentFiles,
            ),
            // The process's scope, so saves and a discarded copy's deletion outlive a recreated activity.
            services.work,
            request,
            ComposeOrigin(
                account.id,
                all.map { ComposeAccount(it.id, it.name) },
                account.ownAddresses,
                behavior.behavior.write.recentFrom,
                behavior.behavior.write.signatureInReplies,
                photoCleaning = { behavior.behavior.write.photoCleaning },
                ownAvatar = { accountId, address ->
                    all.firstOrNull { it.id == accountId }?.let { ownAvatars.avatar(it.own, address) }
                },
            ),
            onSent = { from -> behavior.updateWrite { it.sentFrom(from.address) } },
        )
    }

    /** Every address's signature, for Settings › Mail behavior › Signatures. */
    val signatures =
        SignaturesState(services.drafts, services.signatures, services.pickers.signatureImages, scope) {
            accounts.accounts.map { ComposeAccount(it.id, it.name) }
        }

    /** The draft [message], a saved copy, holds; [whole] keeps its text as it is instead of split for Write. */
    suspend fun savedDraft(
        message: MailMessage,
        whole: Boolean,
    ): Draft? = services.savedDrafts.load(message.location.accountId, message.emailId, whole)

    /** The send still waiting for [message]; null when none waits or the server cannot say. */
    suspend fun held(message: MailMessage): HeldSend? =
        services.scheduled.held(message.location.accountId, message.emailId)

    /** Moves scheduled mail the server has sent to Sent, in every account, in the process's scope. */
    fun fileScheduled() {
        services.work.launch { accounts.accounts.forEach { services.scheduled.fileSent(it.id) } }
    }

    /**
     * Sends drafts after their page closes, with Undo and the outcome in a toast. It runs in the
     * process's scope, so a send outlives a recreated activity.
     */
    val outgoing =
        OutgoingMail(
            services.work,
            services.moveToasts.toasts,
            services.scheduled,
            services.outbox,
            dates,
        ) { behavior.behavior.write.undoSendSeconds.seconds.inWholeMilliseconds }

    /** Send and Discard for a draft, which first cancel the scheduled send it edits. */
    val heldEdits = HeldEdits(services.work, services.moveToasts.toasts, services.scheduled, services.drafts, outgoing)

    /** Send now for a message waiting in Scheduled, which first cancels its send. */
    val sendNow = ScheduledSendNow(services.work, services.moveToasts.toasts, services.scheduled, outgoing)
}
