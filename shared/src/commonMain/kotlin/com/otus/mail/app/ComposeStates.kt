package com.otus.mail.app

import com.otus.accounts.domain.ownAddresses
import com.otus.accounts.presentation.AccountsState
import com.otus.avatar.ui.presentation.OwnAvatarsState
import com.otus.dateformat.DateFormatter
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.HeldSend
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.SentMessage
import com.otus.mail.core.messages.domain.accountId
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.feature.compose.presentation.ComposeAccount
import com.otus.mail.feature.compose.presentation.ComposeOrigin
import com.otus.mail.feature.compose.presentation.ComposeServices
import com.otus.mail.feature.compose.presentation.ComposeState
import com.otus.mail.feature.compose.presentation.OutgoingMail
import com.otus.mail.feature.compose.presentation.SignaturesState
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

    /** The send still waiting for [message]; null when none waits or the server cannot say. */
    suspend fun held(message: MailMessage): HeldSend? =
        services.scheduled.held(message.location.accountId, message.emailId)

    /** Cancels [message], a held or scheduled send, and makes its copy a draft again. */
    suspend fun cancel(message: SentMessage): Result<Unit> = services.scheduled.cancel(message)

    /** Sends [message], a held or scheduled send, at once. */
    suspend fun sendNow(message: SentMessage): Result<Unit> = services.scheduled.sendNow(message)

    /** Moves scheduled mail the server has sent to Sent, in every account, in the process's scope. */
    fun fileScheduled() {
        services.work.launch { accounts.accounts.forEach { services.scheduled.fileSent(it.id) } }
    }

    /**
     * Sends drafts after their page closes, with Undo and the outcome in a toast. It runs in the
     * process's scope, so a send outlives a recreated activity.
     */
    val outgoing =
        OutgoingMail(services.work, services.moveToasts.toasts, services.scheduled, services.outbox, dates) {
            behavior.behavior.write.undoSendSeconds.seconds.inWholeMilliseconds
        }
}
