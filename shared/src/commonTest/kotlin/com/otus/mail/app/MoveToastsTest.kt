package com.otus.mail.app

import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.mail.feature.inbox.presentation.FolderShortcut
import com.otus.mail.feature.inbox.presentation.MoveTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MoveToastsTest {
    private val trash = MoveTarget("t", "Trash", FolderShortcut.Trash)
    private val clients = MoveTarget("c", "Clients")

    @Test
    fun namesTheMoveByWhereTheMailWent() {
        assertEquals("Email deleted", movedText(trash, 1))
        assertEquals("3 emails archived", movedText(MoveTarget("a", "Archive", FolderShortcut.Archive), 3))
        assertEquals("Email marked as spam", movedText(MoveTarget("s", "Spam", FolderShortcut.Spam), 1))
        assertEquals("Moved to Clients", movedText(clients, 1))
        assertEquals("2 emails moved to Clients", movedText(clients, 2))
    }

    @Test
    fun repeatedMovesCountUpInOneToastAndUndoThemAll() =
        runTest {
            val toasts = ToastState()
            val moves = MoveToasts(toasts, this)
            val undone = mutableListOf<Int>()
            (1..3).forEach { n -> moves.moved(trash) { undone += n } }
            moves.moved(clients) { undone += 4 }
            assertEquals(listOf(trash.folderId, clients.folderId), toasts.messages.map { it.group })
            val deleted = toasts.messages.first()
            assertEquals("3 emails deleted", deleted.text)
            deleted.action?.onAction?.invoke()
            runCurrent()
            assertEquals(listOf(1, 2, 3), undone)
        }

    @Test
    fun aNewToastStartsCountingAgain() {
        val toasts = ToastState()
        val moves = MoveToasts(toasts, CoroutineScope(Job()))
        moves.moved(trash) {}
        toasts.messages
            .single()
            .action
            ?.onAction
            ?.invoke()
        moves.moved(trash) {}
        assertEquals("Email deleted", toasts.messages.single().text)
    }
}
