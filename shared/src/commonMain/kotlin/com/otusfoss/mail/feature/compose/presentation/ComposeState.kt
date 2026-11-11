package com.otusfoss.mail.feature.compose.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.otusfoss.core.designsystem.mail.compose.elements.RichText
import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.DraftException
import com.otusfoss.mail.core.messages.domain.DraftFailure
import com.otusfoss.mail.core.messages.domain.DraftRepository
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.SendIdentity
import com.otusfoss.mail.core.messages.domain.Signature
import com.otusfoss.mail.core.messages.domain.answered
import com.otusfoss.mail.core.messages.domain.answeredOriginal
import com.otusfoss.mail.feature.compose.domain.preferredIdentity
import com.otusfoss.mail.feature.compose.domain.startDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime

/**
 * The draft [request] starts in the [origin] account, as the user edits it. Each change saves to the
 * Drafts folder [saveDelayMillis] after the last one, and [close] saves at once; each save
 * replaces the earlier copy. Saves run one at a time, and a started save finishes, so each save knows
 * the copy it replaces. A failed save shows a [notice] until the next one succeeds. [handOff] hands
 * the draft over for sending and reports the address it went from to [onSent].
 * [scope] must outlive the page, so a save started on closing finishes.
 */
class ComposeState(
    services: ComposeServices,
    private val scope: CoroutineScope,
    val request: ComposeRequest,
    private val origin: ComposeOrigin,
    private val onSent: (MailSender) -> Unit = {},
    private val saveDelayMillis: Long = SAVE_DELAY_MILLIS,
) {
    private val drafts = services.drafts
    private val scheduled = services.scheduled
    val accounts: List<ComposeAccount> get() = origin.accounts

    private val start = request.startDraft(origin.accountId, origin.ownAddresses)

    /** What tells an edit: a resumed draft compares with how it began, so it still counts as written. */
    private val unwritten =
        (request as? ComposeRequest.Resume)?.from?.startDraft(origin.accountId, origin.ownAddresses) ?: start

    var draft by mutableStateOf(start)
        private set

    /** The body as the user formats it; [followBody] copies each change into [draft]. */
    val body = RichText(start.html.ifEmpty { start.body.plainHtml() })

    val recipients =
        ComposeRecipients(
            services.addressBook,
            scope,
            accountId = { draft.accountId },
            people = { draft.recipients(it) },
            change = { field, people -> edit { it.withRecipients(field, people) } },
        )

    val attachments =
        ComposeAttachments(
            ComposeAttachmentServices(services.files, services.uploads, origin.photoCleaning),
            scope,
            body,
            accountId = { draft.accountId },
            attachments = { draft.attachments },
            change = { files -> edit { it.copy(attachments = files(it.attachments)) } },
            initialSources = (request as? ComposeRequest.Resume)?.attachmentSources.orEmpty(),
        )

    /** The thread a reply or follow-up answers; null for a new message or a forward. */
    val thread: ReplyThread? =
        request.answeredOriginal?.let { original ->
            ReplyThread(original, services.threads, scope) { quote -> edit { it.copy(quote = quote) } }
        }

    /** Addresses every account may send from, in account order. */
    var identities by mutableStateOf<List<SendIdentity>>(emptyList())
        private set

    var notice by mutableStateOf<String?>(null)
        private set

    /** Whether the draft carries its identity's signature; the ⋯ menu turns it off for this draft. */
    var signatureOn by mutableStateOf(request.startsSigned(origin.signatureInReplies, emptyList()))
        private set

    /** How far ahead each account may schedule, in seconds, by account id. */
    private var delayLimits by mutableStateOf<Map<String, Long>>(emptyMap())

    /** When Send has the server send the draft, as chosen in Send later; null sends it now. */
    var sendAt by mutableStateOf((request as? ComposeRequest.Resume)?.sendAt)

    /** How far ahead the draft's account may schedule it, in seconds; 0 hides Schedule send. */
    val maxDelaySeconds: Long get() = delayLimits[draft.accountId] ?: 0L

    /** The signature the draft goes out with: its From address's, or a resumed copy's until that is known. */
    val signature: Signature
        get() = if (signatureOn) identities.signatureOf(draft.from) ?: request.resumedSignature else Signature()

    /** Whether Send can run: the draft has an address to send from, someone to go to, and its files uploaded. */
    val canSend: Boolean
        get() =
            draft.from != null &&
                draft.recipients.isNotEmpty() &&
                draft.attachments.all { it.uploadedTo(draft.accountId) }

    /** Whether the user has written anything the draft did not start with. */
    val edited: Boolean
        get() = draft.content() != unwritten.content()

    private val saving = Mutex()
    private var pending: Job? = null
    private var stored by mutableStateOf(start.takeIf { it.emailId != null })

    // Undo can resume unsaved content with the id of an older copy.
    private var savedHere = false

    /** Whether the draft is still its stored copy as opened, which needs no save. */
    private var asOpened = (request as? ComposeRequest.Resume)?.asSaved == true

    /** Nothing saves once set: the draft was discarded or sent, or it edits a send that still waits. */
    private var finished = (request as? ComposeRequest.Resume)?.held != null

    /** Loads every account's identities and picks the one to send from, unless the user already chose. */
    fun loadIdentities() {
        scope.launch {
            val limits =
                accounts.map { account ->
                    async { account.id to (scheduled?.maxDelaySeconds(account.id) ?: 0L) }
                }
            val loaded = accounts.map { async { drafts.identities(it.id) } }.awaitAll().flatten()
            val found = loaded.map(origin::withOwnAvatar)
            thread?.load()
            identities = found
            signatureOn = request.startsSigned(origin.signatureInReplies, found)
            delayLimits = limits.awaitAll().toMap()
            if (draft.from == null) {
                val preferred = request.preferredIdentity(found.map { it.sender }, origin.recentFrom)
                val identity = found.firstOrNull { it.sender == preferred }
                if (identity != null) chooseFrom(identity)
            }
        }
    }

    /** Copies every change of [body] into [draft], which saves it. Runs until cancelled. */
    suspend fun followBody() {
        snapshotFlow { body.text to body.html }
            .drop(1)
            .collect { (text, html) -> edit { it.copy(body = text, html = html) } }
    }

    fun edit(change: (Draft) -> Draft) {
        draft = change(draft)
        scheduleSave()
    }

    /**
     * Sends from [identity]. One of another account moves the draft there: the next save stores it
     * in that account's Drafts before the copy in the old one is deleted.
     */
    fun chooseFrom(identity: SendIdentity) {
        val kept = stored?.takeIf { it.accountId == identity.accountId }?.emailId
        edit { it.copy(from = identity.sender, accountId = identity.accountId, emailId = kept) }
    }

    fun showSignature(on: Boolean) {
        signatureOn = on
        scheduleSave()
    }

    /** Saves what is new without waiting; for leaving the page. */
    fun close() {
        pending?.cancel()
        scope.launch { save() }
    }

    /**
     * Stops saving and keeps the stored copy, so Undo can open the draft again. Returns what deletes
     * that copy, after any save in progress, once Undo has passed.
     */
    fun discard(): () -> Unit {
        attachments.stop()
        finished = true
        pending?.cancel()
        return { scope.launch { saving.withLock { stored?.discardCopy(drafts) } } }
    }

    /** [reopening] once any save in progress has ended, so it opens the copy saved last. */
    suspend fun reopeningSaved(): ComposeRequest.Resume = saving.withLock { reopening() }

    /**
     * Stops saving and hands the draft over for sending. Its [OutgoingDraft.ready] waits for any
     * save in progress, so the draft knows its saved copy. Null when the draft has no From address
     * or no recipient. Typed addresses become recipients first.
     */
    fun handOff(): OutgoingDraft? {
        recipients.commitAll()
        if (!canSend) return null
        finished = true
        pending?.cancel()
        val outgoing = draft.copy(signature = signature)
        return OutgoingDraft(
            ready = {
                saving.withLock {
                    outgoing.copy(emailId = stored?.takeIf { it.accountId == outgoing.accountId }?.emailId)
                }
            },
            answered = request.answered(outgoing.accountId),
            sent = {
                // A copy saved in another account before the From address changed has no use now.
                saving.withLock { stored?.takeIf { it.accountId != outgoing.accountId }?.discardCopy(drafts) }
                val from = outgoing.from
                if (from != null) onSent(from)
            },
        )
    }

    private fun scheduleSave() {
        asOpened = false
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
            val savedId = stored?.takeIf { it.accountId == draft.accountId }?.emailId
            val current = draft.copy(emailId = savedId, signature = signature)
            val unchanged =
                if (stored == null) {
                    !edited
                } else {
                    savedHere && current.copy(emailId = null) == stored?.copy(emailId = null)
                }
            if (finished || unchanged || asOpened) return@withLock
            drafts.save(current).fold(
                onSuccess = { id ->
                    val previous = stored
                    stored = current.copy(emailId = id)
                    savedHere = true
                    // The user may have moved the draft to another account while it saved.
                    if (draft.accountId == current.accountId) draft = draft.copy(emailId = id)
                    notice = null
                    previous?.takeIf { it.accountId != current.accountId }?.discardCopy(drafts)
                },
                onFailure = {
                    notice = ((it as? DraftException)?.failure ?: DraftFailure.Mail(MailFailure.ServerProblem)).notice
                },
            )
        }
}

private suspend fun Draft.discardCopy(drafts: DraftRepository) {
    val savedId = emailId ?: return
    drafts.discard(accountId, savedId)
}

/**
 * What the user wrote, without the sending identity, the account, the stored copy's id, or the
 * signature, which follows the identity.
 */
private fun Draft.content(): Draft = copy(from = null, accountId = "", emailId = null, signature = Signature())

/** Plain text as paragraphs, for a body that starts without formatting. */
private fun String.plainHtml(): String =
    if (isEmpty()) {
        ""
    } else {
        lines().joinToString("") { "<p>" + it.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</p>" }
    }

private const val SAVE_DELAY_MILLIS = 2_000L
