package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.otus.designsystem.elements.RichText
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftException
import com.otus.mail.feature.inbox.domain.DraftFailure
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.OriginalMessage
import com.otus.mail.feature.inbox.domain.SendIdentity
import com.otus.mail.feature.inbox.domain.SentMessage
import com.otus.mail.feature.inbox.domain.Signature
import com.otus.mail.feature.inbox.domain.answered
import com.otus.mail.feature.inbox.domain.preferredIdentity
import com.otus.mail.feature.inbox.domain.signature
import com.otus.mail.feature.inbox.domain.startDraft
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
import kotlin.time.Instant

/**
 * The draft [request] starts in the [origin] account, as the user edits it. Each change saves to the
 * Drafts folder [saveDelayMillis] after the last one, and [close] saves at once; each save
 * replaces the earlier copy. Saves run one at a time, and a started save finishes, so each save knows
 * the copy it replaces. A failed save shows a [notice] until the next one succeeds. [handOff] hands
 * the draft to the sender of [services] and reports the address it went from to [onSent].
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
    private val sender = services.sender
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
            services.files,
            services.uploads,
            scope,
            body,
            accountId = { draft.accountId },
            attachments = { draft.attachments },
            change = { files -> edit { it.copy(attachments = files(it.attachments)) } },
        )

    /** The thread a reply or follow-up answers; null for a new message or a forward. */
    val thread: ReplyThread? =
        request.answeredOriginal?.let {
            ReplyThread(it, services.threads, scope) { quote ->
                edit { d ->
                    d.copy(quote = quote)
                }
            }
        }

    /** Addresses every account may send from, in account order. */
    var identities by mutableStateOf<List<SendIdentity>>(emptyList())
        private set

    var notice by mutableStateOf<String?>(null)
        private set

    /** Whether the draft carries its identity's signature; the ⋯ menu turns it off for this draft. */
    var signatureOn by mutableStateOf(request is ComposeRequest.New || origin.signatureInReplies)
        private set

    /** How far ahead each account may schedule, in seconds, by account id. */
    private var delayLimits by mutableStateOf<Map<String, Long>>(emptyMap())

    /** When Send has the server send the draft, as chosen in Send later; null sends it now. */
    var sendAt by mutableStateOf<LocalDateTime?>(null)

    /** How far ahead the draft's account may schedule it, in seconds; 0 hides Schedule send. */
    val maxDelaySeconds: Long get() = delayLimits[draft.accountId] ?: 0L

    /** The signature the draft goes out with: its From address's, while [signatureOn]. */
    val signature: Signature
        get() = if (signatureOn) identities.signatureOf(draft.from) else Signature()

    /** Whether Send can run: the draft has an address to send from, someone to go to, and its files uploaded. */
    val canSend: Boolean
        get() =
            draft.from != null &&
                draft.recipients.isNotEmpty() &&
                draft.attachments.all { it.uploadedTo(draft.accountId) }

    /** Whether the user has written anything the draft did not start with. */
    val edited: Boolean
        get() = draft.content() != unwritten.content()

    /** Whether the server holds the draft as it is now. */
    val saved: Boolean
        get() = draft.emailId != null && draft.content() == stored?.content()

    private val saving = Mutex()
    private var pending: Job? = null
    private var stored by mutableStateOf<Draft?>(null)

    /** Set once the draft is discarded or sent; nothing saves after that. */
    private var finished = false

    /** Loads every account's identities and picks the one to send from, unless the user already chose. */
    fun loadIdentities() {
        scope.launch {
            val limits =
                accounts.map { account ->
                    async { account.id to (scheduled?.maxDelaySeconds(account.id) ?: 0L) }
                }
            val found = accounts.map { async { drafts.identities(it.id) } }.awaitAll().flatten()
            thread?.load()
            identities = found
            delayLimits = limits.awaitAll().toMap()
            if (draft.from ==
                null
            ) {
                draft = draft.copy(from = request.preferredIdentity(found.map { it.sender }, origin.recentFrom))
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
     * in that account's Drafts, and the copy in the old one is deleted.
     */
    fun chooseFrom(identity: SendIdentity) {
        val left = draft.takeIf { it.accountId != identity.accountId }
        val kept = draft.emailId.takeIf { left == null }
        edit { it.copy(from = identity.sender, accountId = identity.accountId, emailId = kept) }
        left?.emailId?.let { id -> scope.launch { saving.withLock { drafts.discard(left.accountId, id) } } }
    }

    /** Adds or removes the signature in this draft only. */
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
        finished = true
        pending?.cancel()
        return { scope.launch { saving.withLock { draft.emailId?.let { drafts.discard(draft.accountId, it) } } } }
    }

    /**
     * Stops saving and hands the draft over for sending: the returned send waits for any save in
     * progress, so it knows the saved copy, and sends at `at`, or now when that is null. Null when
     * the draft has no From address or no recipient. Typed addresses become recipients first.
     */
    fun handOff(): (suspend (at: Instant?) -> Result<SentMessage>)? {
        recipients.commitAll()
        if (!canSend) return null
        finished = true
        pending?.cancel()
        val outgoing = draft.copy(signature = signature)
        val answered = request.answered(outgoing.accountId)
        return { at ->
            saving
                .withLock { sender.send(outgoing.copy(emailId = draft.emailId), answered, at) }
                .onSuccess { outgoing.from?.let(onSent) }
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
            val current = draft.copy(signature = signature)
            val unchanged = if (current.emailId == null) !edited else current.content() == stored?.content()
            if (finished || unchanged) return@withLock
            drafts.save(current).fold(
                onSuccess = { id ->
                    stored = current
                    // The user may have moved the draft to another account while it saved.
                    if (draft.accountId == current.accountId) draft = draft.copy(emailId = id)
                    notice = null
                },
                onFailure = { notice = ((it as? DraftException)?.failure ?: DraftFailure.ServerProblem).notice },
            )
        }
}

private fun List<SendIdentity>.signatureOf(from: MailSender?): Signature =
    firstOrNull { it.sender.address.equals(from?.address, ignoreCase = true) }?.signature ?: Signature()

/** Every recipient once, in line order, for the bar's context. */
val Draft.recipients: List<MailSender>
    get() = (to + cc + bcc).distinctBy { it.address.lowercase() }

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

/** The request that opens this draft again as it is, such as after Undo. */
fun ComposeState.reopening(): ComposeRequest.Resume =
    ComposeRequest.Resume(draft, (request as? ComposeRequest.Resume)?.from ?: request)

/** The message a reply or follow-up answers, whose thread it shows. */
private val ComposeRequest.answeredOriginal: OriginalMessage?
    get() =
        when (this) {
            is ComposeRequest.Reply -> original
            is ComposeRequest.FollowUp -> original
            is ComposeRequest.Resume -> from.answeredOriginal
            is ComposeRequest.New, is ComposeRequest.Forward -> null
        }
