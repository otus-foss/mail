package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftException
import com.otus.mail.feature.inbox.domain.DraftFailure
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.DraftSender
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.SendException
import com.otus.mail.feature.inbox.domain.SendFailure
import com.otus.mail.feature.inbox.domain.answered
import com.otus.mail.feature.inbox.domain.parseRecipients
import com.otus.mail.feature.inbox.domain.preferredIdentity
import com.otus.mail.feature.inbox.domain.recipientText
import com.otus.mail.feature.inbox.domain.startDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A line of people a draft goes to. */
enum class RecipientField { To, Cc, Bcc }

/**
 * The draft [request] starts in [accountId], as the user edits it. Each change saves to the
 * Drafts folder [saveDelayMillis] after the last one, and [close] saves at once; each save
 * replaces the earlier copy. Saves run one at a time, and a started save finishes, so each save knows
 * the copy it replaces. A failed save shows a [notice] until the next one succeeds. [send] hands
 * the draft to [sender]. [scope] must outlive the page, so a save started on closing finishes.
 */
class ComposeState(
    private val drafts: DraftRepository,
    private val sender: DraftSender,
    private val scope: CoroutineScope,
    val request: ComposeRequest,
    accountId: String,
    ownAddresses: Collection<String>,
    private val saveDelayMillis: Long = SAVE_DELAY_MILLIS,
) {
    private val start = request.startDraft(accountId, ownAddresses)

    var draft by mutableStateOf(start)
        private set

    /** The recipient lines as typed, which [draft] holds parsed. */
    var recipientText by mutableStateOf(RecipientField.entries.associateWith { start.recipients(it).recipientText() })
        private set

    /** Addresses the account may send from; the From line offers a choice when there are several. */
    var identities by mutableStateOf<List<MailSender>>(emptyList())
        private set

    var notice by mutableStateOf<String?>(null)
        private set

    /** Whether a [send] is under way. */
    var sending by mutableStateOf(false)
        private set

    /** Whether Send can run: the draft has an address to send from and someone to go to. */
    val canSend: Boolean
        get() = !sending && draft.from != null && draft.recipients.isNotEmpty()

    /** Whether the user has written anything the draft did not start with. */
    val edited: Boolean
        get() = draft.content() != start.content()

    private val saving = Mutex()
    private var pending: Job? = null
    private var stored: Draft? = null

    /** Set once the draft is discarded or sent; nothing saves after that. */
    private var finished = false

    /** Loads the identities and picks the one to send from, unless the user already chose. */
    fun loadIdentities() {
        scope.launch {
            val found = drafts.identities(draft.accountId)
            identities = found
            if (draft.from == null) draft = draft.copy(from = request.preferredIdentity(found))
        }
    }

    fun edit(change: (Draft) -> Draft) {
        draft = change(draft)
        scheduleSave()
    }

    fun editRecipients(
        field: RecipientField,
        text: String,
    ) {
        recipientText += field to text
        edit { it.withRecipients(field, parseRecipients(text)) }
    }

    /** Saves what is new without waiting; for leaving the page. */
    fun close() {
        pending?.cancel()
        scope.launch { save() }
    }

    /** Stops saving and deletes the stored copy, after any save in progress. */
    fun discard() {
        finished = true
        pending?.cancel()
        scope.launch { saving.withLock { draft.emailId?.let { drafts.discard(draft.accountId, it) } } }
    }

    /**
     * Sends the draft once any save in progress ends, then runs [onSent]. A failed send shows a
     * [notice] and leaves the draft to edit and save as before.
     */
    fun send(onSent: () -> Unit) {
        if (!canSend) return
        pending?.cancel()
        sending = true
        scope.launch {
            val result =
                saving.withLock {
                    sender.send(draft, request.answered(draft.accountId)).onSuccess { finished = true }
                }
            sending = false
            result.fold(
                onSuccess = {
                    notice = null
                    onSent()
                },
                onFailure = { notice = ((it as? SendException)?.failure ?: SendFailure.ServerProblem).notice },
            )
        }
    }

    private fun scheduleSave() {
        pending?.cancel()
        pending =
            scope.launch {
                delay(saveDelayMillis)
                // A later change, close, or discard cancels only the wait. The server may already hold
                // the new copy, so a started save finishes and keeps its id for the next one to replace.
                withContext(NonCancellable) { save() }
            }
    }

    private suspend fun save() =
        saving.withLock {
            val current = draft
            val unchanged =
                if (current.emailId ==
                    null
                ) {
                    !edited
                } else {
                    current.copy(emailId = null) == stored?.copy(emailId = null)
                }
            if (finished || unchanged) return@withLock
            drafts.save(current).fold(
                onSuccess = { id ->
                    stored = current
                    draft = draft.copy(emailId = id)
                    notice = null
                },
                onFailure = { notice = ((it as? DraftException)?.failure ?: DraftFailure.ServerProblem).notice },
            )
        }
}

/** Every recipient once, in line order, for the bar's context. */
val Draft.recipients: List<MailSender>
    get() = (to + cc + bcc).distinctBy { it.address.lowercase() }

private fun Draft.recipients(field: RecipientField): List<MailSender> =
    when (field) {
        RecipientField.To -> to
        RecipientField.Cc -> cc
        RecipientField.Bcc -> bcc
    }

private fun Draft.withRecipients(
    field: RecipientField,
    people: List<MailSender>,
): Draft =
    when (field) {
        RecipientField.To -> copy(to = people)
        RecipientField.Cc -> copy(cc = people)
        RecipientField.Bcc -> copy(bcc = people)
    }

/** What the user wrote, without the sending identity or the stored copy's id. */
private fun Draft.content(): Draft = copy(from = null, emailId = null)

private val DraftFailure.notice: String
    get() =
        when (this) {
            DraftFailure.NoDraftsFolder -> "This account has no Drafts folder, so the draft isn't saved."
            DraftFailure.NotPermitted -> "The server doesn't allow saving this draft."
            DraftFailure.Unreachable -> "Couldn't reach the server. The draft saves with your next change."
            DraftFailure.ServerProblem -> "Couldn't save the draft. It saves again with your next change."
        }

private val SendFailure.notice: String
    get() =
        when (this) {
            SendFailure.NoIdentity -> "The server doesn't let this account send from this address."
            SendFailure.Recipients -> "Check the recipients. The server can't send to one of them."
            SendFailure.TooManyRecipients -> "Too many recipients for this server. Remove some and try again."
            SendFailure.TooLarge -> "The message is too large for this server to send."
            SendFailure.NotPermitted -> "The server doesn't allow sending this message."
            SendFailure.Unreachable -> "Lost the connection while sending. Check Sent before you try again."
            SendFailure.ServerProblem -> "Couldn't send the message. Try again."
        }

private const val SAVE_DELAY_MILLIS = 2_000L
