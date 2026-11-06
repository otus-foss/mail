package com.otus.mail.core.messages.presentation

import com.otus.mail.core.messages.domain.EmailWriter
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * The changes the user makes to mail, shown before the server saves them. Every list and Read
 * view that [observe]s sees each edit at once, and sees it kept or rolled back once [writer]
 * answers. Saves run in [scope], the screen's; Undo and Retry run in [toasts]' work, which
 * outlives the screen, and [afterUndo] then reloads whatever screen shows by then. [changed]
 * emits once the server saved edits that change folder counts.
 */
class MailEdits(
    internal val writer: EmailWriter,
    internal val scope: CoroutineScope,
    internal val toasts: MoveToasts,
    private val afterUndo: () -> Unit = {},
) {
    private val changes =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val changed: SharedFlow<Unit> get() = changes

    private var observers = listOf<EditObserver>()
    private var unsettled = listOf<MailEdit>()
    private var saved = listOf<SavedEdit>()
    private var settledCount = 0

    /** Shows every edit to [observer] from now on, until the returned function runs. */
    internal fun observe(observer: EditObserver): () -> Unit {
        observers = observers + observer
        return { observers = observers - observer }
    }

    /**
     * Where a load that starts now stands; [since] that checkpoint gives the edits the load may
     * miss, because they were not saved before it started.
     */
    internal fun checkpoint(): Int = settledCount

    internal fun since(checkpoint: Int): List<MailEdit> =
        saved.filter { it.at > checkpoint }.map { it.edit } + unsettled

    /** Shows [edit] everywhere until it is settled. */
    internal fun show(edit: MailEdit): MailEdit {
        unsettled = unsettled + edit
        observers.forEach { it.shown(edit) }
        return edit
    }

    /** Keeps each edit the server saved and rolls back the others, then announces changed counts once. */
    internal fun settle(outcomes: Map<MailEdit, Boolean>) {
        settledCount++
        unsettled = unsettled - outcomes.keys
        saved = (saved + outcomes.filterValues { it }.keys.map { SavedEdit(it, settledCount) }).takeLast(MAX_SAVED)
        outcomes.forEach { (edit, kept) -> observers.forEach { it.settled(edit, kept) } }
        if (outcomes.any { (edit, kept) -> kept && edit.counts }) changes.tryEmit(Unit)
    }

    internal fun settle(
        edit: MailEdit,
        kept: Boolean,
    ) = settle(mapOf(edit to kept))

    /** Tells folder counts that mail changed, as after emptying a folder. */
    internal fun announce() {
        changes.tryEmit(Unit)
    }

    /** Takes back a saved [edit], as after its Undo reached the server. */
    internal fun revert(edit: MailEdit) {
        observers.forEach { it.reverted(edit) }
    }

    /**
     * Shows [edit] and asks the server to [save] it. A saved edit stays; a refused one is rolled
     * back, [onRefused] runs, and [feed] reports [failure]. The returned job ends with the answer.
     */
    internal fun save(
        edit: MailEdit,
        feed: EditedList,
        failure: String,
        onRefused: () -> Unit = {},
        save: suspend () -> Boolean,
    ): Job {
        show(edit)
        return scope.launch {
            val kept = save()
            settle(edit, kept)
            if (!kept) {
                onRefused()
                feed.report(failure)
            }
        }
    }

    /** Shows the outcome of a move's Undo, which may outlive the screen that offered it. */
    internal fun undone(done: Boolean) {
        if (done) afterUndo() else toasts.failedUndo()
    }

    /** Reloads the screen on show after work that outlived the screen that started it changed mail. */
    internal fun reloadAfterUndo() = afterUndo()

    private class SavedEdit(
        val edit: MailEdit,
        val at: Int,
    )

    private companion object {
        /** Loads finish within moments, so they never miss more saved edits than these. */
        const val MAX_SAVED = 64
    }
}

/**
 * One change to the list row [rowId] and the emails [emailIds] it holds. When the row [leaves] a
 * list, only that list takes it off; otherwise [row] changes it there, and [undo] sets it back on
 * a rollback. [page] changes each Read view page of those emails, or drops it when it gives null.
 * [counts] marks an edit that changes folder counts.
 */
class MailEdit internal constructor(
    val rowId: String,
    val emailIds: Set<String>,
    val leaves: MailScope? = null,
    val row: ((MailMessage) -> MailMessage)? = null,
    val undo: (MailMessage) -> MailMessage = { it },
    val page: ((MailMessage) -> MailMessage?)? = null,
    val counts: Boolean = true,
)

/** A list of mail that edits act on: what it shows, and how it reloads and reports a refused edit. */
interface EditedList {
    val mailScope: MailScope
    val messages: List<MailMessage>

    fun refresh()

    fun report(text: String)
}

/** A list or Read view that shows edits as they happen. */
internal interface EditObserver {
    fun shown(edit: MailEdit)

    fun settled(
        edit: MailEdit,
        kept: Boolean,
    )

    fun reverted(edit: MailEdit) {}
}
