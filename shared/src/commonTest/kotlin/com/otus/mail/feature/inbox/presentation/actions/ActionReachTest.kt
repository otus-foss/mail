package com.otus.mail.feature.inbox.presentation.actions

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.ActionBarLayout
import com.otus.mail.core.messages.domain.ActionBarPlace
import com.otus.mail.core.messages.domain.BarButton
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.OwnAddresses
import com.otus.mail.core.messages.domain.replyAction
import com.otus.mail.core.messages.presentation.FolderCleanup
import com.otus.mail.core.messages.presentation.MessageActions
import com.otus.mail.core.messages.presentation.MessageMarks
import com.otus.mail.core.messages.presentation.MessageMoves
import com.otus.mail.core.messages.presentation.MoveTarget
import com.otus.mail.core.messages.presentation.ReadLaterChange
import com.otus.mail.core.messages.presentation.actions.ActionSubject
import com.otus.mail.core.messages.presentation.actions.MailBar
import com.otus.mail.core.messages.presentation.actions.MoveOptions
import com.otus.mail.core.messages.presentation.actions.OfferedAction
import com.otus.mail.core.messages.presentation.actions.PageSubject
import com.otus.mail.core.messages.presentation.actions.barFor
import com.otus.mail.core.messages.presentation.actions.offer
import com.otus.mail.core.settings.domain.ActionBehavior
import com.otus.mail.feature.read.presentation.ReadPage
import com.otus.mail.feature.read.presentation.readMenu
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every action a place offers can be reached there, in some folder, whichever way the bar is set:
 * from the Read view's bar or ⋯ menu, and from the selection's bar or its All actions menu.
 */
class ActionReachTest {
    private val own = OwnAddresses(mapOf("ada" to listOf("me@example.org")))

    private val folders =
        listOf(
            MoveTarget("inbox", listOf("Inbox"), MailboxRole.Inbox),
            MoveTarget("archive", listOf("Archive"), MailboxRole.Archive),
            MoveTarget("trash", listOf("Trash"), MailboxRole.Trash),
            MoveTarget("junk", listOf("Spam"), MailboxRole.Junk),
            MoveTarget("receipts", listOf("Receipts"), own = true, path = listOf("Receipts")),
        )

    /** Full and minimized bars, each with the default buttons and with a Read bar that has no reply. */
    private val behaviors =
        listOf(false, true).flatMap { minimized ->
            val base = ActionBehavior(minimizedBar = minimized)
            val noReply = ActionBarPlace.Message to ActionBarLayout(emptyList(), MailAction.Read)
            listOf(base, base.copy(bars = base.bars + noReply))
        }

    private val roles = listOf(MailboxRole.Inbox, MailboxRole.Sent, MailboxRole.Junk, MailboxRole.Trash)

    @Test
    fun everyReadViewActionIsOnTheBarOrInTheMenu() {
        val reached =
            behaviors.flatMap { behavior ->
                roles.flatMap { role -> listOf(1, 3).flatMap { count -> readViewReach(behavior, role, count) } }
            }
        assertEquals(offered(ActionBarPlace.Message), reached.toSet())
    }

    @Test
    fun readLaterIsInTheMenuWhileTheBarLeavesItOut() {
        val reached = readViewReach(ActionBehavior(), MailboxRole.Inbox, count = 3)
        assertEquals(true, MailAction.ReadLater in reached)
    }

    @Test
    fun everySelectionActionIsOnTheBarOrInItsMenus() {
        val reached =
            behaviors.flatMap { behavior ->
                roles.flatMap { role ->
                    val (bar, offers) =
                        barOf(
                            behavior,
                            ActionBarPlace.Selection,
                            role,
                            subject(role, listOf(message())),
                        )
                    val shown = if (bar.minimized) listOf(bar.layout.primary) else bar.layout.buttons
                    shown.filter { bar.offer(it) != null } + (bar.more() + bar.every).map(offers::getValue)
                }
            }
        assertEquals(offered(ActionBarPlace.Selection), reached.toSet())
    }

    /** What the Read view reaches for a page of [count] messages in a [role] folder. */
    private fun readViewReach(
        behavior: ActionBehavior,
        role: MailboxRole,
        count: Int,
    ): List<BarButton> {
        val page = message()
        val subject = subject(role, listOf(page), page)
        val (bar, offers) = barOf(behavior, ActionBarPlace.Message, role, subject, page.replyAction(own))
        val menu = readMenu(ReadPage(page, own, count = count))
        val shown = if (bar.minimized) listOf(bar.layout.primary) else bar.layout.buttons
        return (shown + menu.placed).filter { subject.offer(it) != null } + bar.more(menu.covers).map(offers::getValue)
    }

    private fun barOf(
        behavior: ActionBehavior,
        place: ActionBarPlace,
        role: MailboxRole,
        subject: ActionSubject,
        reply: MailAction? = null,
    ): Pair<MailBar, Map<OfferedAction, BarButton>> {
        val offers = mutableMapOf<OfferedAction, BarButton>()
        val offer = { button: BarButton -> subject.offer(button)?.also { offers[it] = button } }
        return behavior.barFor(place, role, offer, reply) to offers
    }

    private fun offered(place: ActionBarPlace): Set<BarButton> = (place.actions - MailAction.AllActions).toSet()

    private fun subject(
        role: MailboxRole,
        messages: List<MailMessage>,
        page: MailMessage? = null,
    ) = ActionSubject(
        actions(role),
        messages,
        moves = MoveOptions(suggestion = folders.last()),
        page = page?.let { PageSubject(it, role) {} },
    )

    private fun actions(role: MailboxRole) =
        MessageActions(
            moves =
                MessageMoves(
                    targets = { folders },
                    onMove = { _, _ -> },
                    suggested = { folders.last() },
                    futureSorting = { null },
                ),
            marks = MessageMarks(can = { _, _ -> true }, set = { _, _, _ -> }),
            ruleForSender = { {} },
            readLater = { ReadLaterChange(saves = true, leaves = false) {} },
            saved = { ActionBehavior() },
            markReadOnOpen = { true },
            cleanup =
                FolderCleanup(
                    role = { role },
                    listRole = { role },
                    onNotSpam = { _, _ -> },
                    canDelete = { true },
                    onDelete = {},
                    emptying = { null },
                ),
        )

    /** Ada's message to the user and two others, so the big button replies to all. */
    private fun message() =
        offerMessage("1").copy(
            to = listOf(MailSender("Me", "me@example.org"), MailSender("Grace", "grace@example.org")),
            cc = listOf(MailSender("Bob", "bob@example.org")),
        )
}
