package com.otus.mail

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.otus.mail.feature.inbox.domain.ConversationMessages
import com.otus.mail.feature.inbox.domain.ConversationMute
import com.otus.mail.feature.inbox.domain.FolderChange
import com.otus.mail.feature.inbox.domain.KnownPeople
import com.otus.mail.feature.inbox.domain.MailAttachment
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailConversation
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MailingList
import com.otus.mail.feature.inbox.domain.MessageContent
import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.domain.MessageHeaders
import com.otus.mail.feature.inbox.domain.MessageHtml
import com.otus.mail.feature.inbox.domain.MessagePrinter
import com.otus.mail.feature.inbox.domain.Newsletters
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.domain.RemoteImages
import com.otus.mail.feature.inbox.domain.SenderBlocks
import com.otus.mail.feature.inbox.domain.ThreadEmail
import com.otus.mail.feature.inbox.domain.ThreadMember
import com.otus.mail.feature.inbox.domain.Unsubscribe
import com.otus.mail.feature.inbox.domain.UnsubscribeResult
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.MessageMarks
import com.otus.mail.feature.inbox.presentation.MessageMoves
import com.otus.mail.feature.inbox.presentation.MoveTarget
import com.otus.mail.feature.inbox.presentation.PageChanges
import com.otus.mail.feature.inbox.presentation.ReadContext
import com.otus.mail.feature.inbox.presentation.ReadDisplay
import com.otus.mail.feature.inbox.presentation.ReadEffects
import com.otus.mail.feature.inbox.presentation.ReadFiles
import com.otus.mail.feature.inbox.presentation.ReadFolders
import com.otus.mail.feature.inbox.presentation.ReadPrivacy
import com.otus.mail.feature.inbox.presentation.ReadServices
import com.otus.mail.feature.inbox.presentation.components.NewFolderSheet
import kotlinx.datetime.LocalDate

/** Demo mail for the Read view: the Q4 launch thread, an invoice, and a Sentry digest. Alex reads it. */
internal object ReadViewFixtures {
    private fun person(
        name: String,
        address: String,
    ) = MailSender(name, address)

    val alex = person("Alex Doe", "alex@otus.example")
    val grace = person("Grace Hopper", "grace@navy.example")
    val radia = person("Radia Perlman", "radia@net.example")
    val katherine = person("Katherine Johnson", "katherine@nasa.example")
    val margaret = person("Margaret Knuth", "margaret@legal.example")
    val ada = person("Ada Lovelace", "ada@lovelace.example")
    private val sentry = person("Sentry", "noreply@sentry.example")
    private val team = listOf(alex, grace, radia, katherine, margaret, ada)

    val folders =
        listOf(
            MessageFolder("in", listOf("Inbox"), role = "inbox"),
            MessageFolder("cl", listOf("Clients"), color = "Blue"),
            MessageFolder("fn", listOf("Finance"), color = "Yellow"),
            MessageFolder("fi", listOf("Finance", "Invoices"), color = "Yellow"),
            MessageFolder("fr", listOf("Freelancing"), color = "Pink"),
            MessageFolder("ar", listOf("Archive 2025"), color = "Green"),
            MessageFolder("sp", listOf("Junk"), role = "junk"),
            MessageFolder("tr", listOf("Trash"), role = "trash"),
        )

    private fun message(
        id: String,
        from: MailSender,
        subject: String,
        body: String,
        received: ReceivedAt,
        folders: Set<String> = setOf("in"),
    ) = MailMessage(
        id,
        from,
        subject,
        body,
        received,
        MailLocation("Inbox", "Alex", "acc", "alex@otus.example", mailboxId = "in", mailboxIds = folders),
        emailId = id,
        to = team.filter { it != from },
        preview = body.lineSequence().firstOrNull { it.isNotBlank() && it != "Hi all," }.orEmpty(),
    )

    val thread =
        listOf(
            message("g", grace, Q4, GRACE, ReceivedAt("2026-10-12", "09:14"), setOf("in", "fi")),
            message("y", alex, Q4, "Can we meet Thursday to settle it?", ReceivedAt("2026-10-11", "18:02")),
            message("r", radia, Q4, "Infra is fine with either plan.", ReceivedAt("2026-10-11", "11:40")),
            message("k", katherine, Q4, KATHERINE, ReceivedAt("2026-10-10", "16:20")),
            message("m", margaret, Q4, MARGARET, ReceivedAt("2026-10-10", "08:55")),
            message("a", ada, Q4, "Kicking this off. Draft plan attached.", ReceivedAt("2026-10-09", "10:00")),
        )

    /** The thread's row; its newest email is in Inbox and also in Finance / Invoices. */
    val threadRow =
        thread.first().copy(
            conversation =
                MailConversation(
                    "t1",
                    thread.size,
                    ThreadEmail(grace, emptyList(), thread.first().received),
                    thread.map { it.sender },
                    answered = false,
                    members = thread.map { ThreadMember(it.emailId, it.location.mailboxIds, "in") },
                ),
        )

    val invoice =
        message("inv", ada, "Invoice October", INVOICE, ReceivedAt("2026-10-18", "09:14"), setOf("cl", "fi"))
            .copy(to = listOf(alex))

    val digest =
        message("s", sentry, "Weekly report for Otus", DIGEST, ReceivedAt("2026-10-16", "07:00"))
            .copy(to = listOf(alex))

    val attachments =
        listOf(
            MailAttachment("Q4-plan-v3.pdf", "application/pdf", 245_760, "b1"),
            MailAttachment("whiteboard.jpg", "image/jpeg", 1_902_009, "b2"),
            MailAttachment("numbers.xlsx", SHEET_TYPE, 1_992_294, "b3"),
        )

    /** Each message's body: the digest is HTML with a remote image and a list to leave. */
    fun content(message: MailMessage): MessageContent =
        when (message.id) {
            "s" -> digestContent()
            "g" -> MessageContent(message.body, truncated = false, html = null, attachments = attachments)
            else -> MessageContent(message.body, truncated = false, html = null)
        }

    private fun digestContent() =
        MessageContent(
            DIGEST,
            truncated = false,
            html = MessageHtml(DIGEST_HTML, remoteContent = true),
            unsubscribe = Unsubscribe(mail = "mailto:unsubscribe@sentry.example"),
            listName = "Sentry weekly digest",
        )

    fun read(
        pages: List<MailMessage>,
        cameFrom: Set<String> = emptySet(),
        applied: (FolderChange) -> Unit = {},
        unsubscribed: () -> Unit = {},
        newFolder: NewFolderSheet? = null,
        changes: PageChanges = RecordedPageChanges(),
        effects: ReadEffects = ReadEffects(),
    ) = ReadContext(
        pages = ConversationMessages { pages },
        folders =
            ReadFolders(
                of = { folders },
                cameFrom = { cameFrom },
                apply = { change, _ -> applied(change) },
                newFolder = newFolder,
            ),
        privacy = ReadPrivacy({ RemoteImages.Known }, known = KnownPeople { team.map { it.address }.toSet() }),
        files =
            ReadFiles(
                downloads = PhotoAttachments(::photo),
                printer = MessagePrinter { _, _ -> },
            ),
        services =
            ReadServices(
                newsletters = FakeNewsletters(unsubscribed),
                headers = MessageHeaders { emptyList() },
                mute = FakeMute(),
                blocks = SenderBlocks { true },
                changes = changes,
            ),
        effects = effects,
        display = ReadDisplay(today = { LocalDate(2026, 10, 19) }),
    )

    /** This context with replies showing their quotes, as the "Show quotes" setting does. */
    fun ReadContext.showingQuotes(): ReadContext =
        ReadContext(pages, folders, privacy, files, services, effects, ReadDisplay({ true }, today = display.today))

    /** A small two-tone picture as PNG bytes, standing in for a downloaded photo. */
    private fun photo(): ByteArray {
        val bitmap = Bitmap.createBitmap(PHOTO_SIZE, PHOTO_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(0x3b, 0x5a, 0x6e))
            drawCircle(
                PHOTO_SIZE * 0.6f,
                PHOTO_SIZE * 0.4f,
                PHOTO_SIZE * 0.25f,
                Paint().apply {
                    color = Color.rgb(0xe8, 0xc4, 0x6a)
                },
            )
        }
        return bitmap.pngBytes()
    }

    fun actions(
        behavior: MailBehavior = MailBehavior(),
        targets: List<MoveTarget> = emptyList(),
    ) = MessageActions(
        moves = MessageMoves(targets = { targets }, onMove = { _, _ -> }, suggested = { null }) { null },
        marks = MessageMarks(can = { _, _ -> true }, set = { _, _, _ -> }),
        ruleForSender = { null },
        readLater = { null },
        behavior = { behavior },
    )

    private class FakeNewsletters(
        private val unsubscribed: () -> Unit,
    ) : Newsletters {
        override suspend fun list(message: MailMessage) =
            MailingList("Sentry weekly digest", 1.0, List(OLDER) { "old$it" })

        override suspend fun unsubscribe(
            message: MailMessage,
            how: Unsubscribe,
        ): UnsubscribeResult {
            unsubscribed()
            return UnsubscribeResult.Done
        }

        override suspend fun archive(
            accountId: String,
            emailIds: List<String>,
        ) = true
    }

    private class FakeMute : ConversationMute {
        override suspend fun isMuted(message: MailMessage) = false

        override suspend fun setMuted(
            message: MailMessage,
            muted: Boolean,
        ) = true
    }

    private const val OLDER = 23
    private const val PHOTO_SIZE = 96
    private const val SHEET_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val DIGEST_HTML =
        "<p>Your weekly digest is here.</p><p>12 new issues in otus-mail, 3 regressions.</p>" +
            "<img src=\"https://sentry.example/chart.png\" width=\"320\" height=\"120\"><p>View all issues</p>"
    private const val Q4 = "Q4 launch plan"
    private const val GRACE =
        "Hi all,\n\nLet's lock it in on Thursday at 10. Two waves, EU second, never on a Friday. " +
            "I'll book the room and send an invite.\n\nGrace"
    private const val MARGARET =
        "Hi all,\n\nI went through the plan with legal this morning. Short version: two waves work, but the EU " +
            "wave needs more lead time than we assumed.\n\n1. German terms: the updated AGB need a full review.\n" +
            "2. Consent banner: the new cookie categories have to ship before the EU wave.\n\n" +
            "Proposal: US wave on 4 Nov as planned, EU wave on 18 Nov at the earliest."
    private const val KATHERINE =
        "Sounds good, Thursday 10 it is. I'll bring the updated numbers.\n\nKatherine\n\n" +
            "On 10 Oct 2026, Margaret Knuth <margaret@legal.example> wrote:\n" +
            "> I went through the plan with legal this morning. Short version: two waves work,\n" +
            "> but the EU wave needs more lead time than we assumed.\n> \n> Margaret"
    private const val INVOICE =
        "Hi Alex,\n\nattached is the invoice for October. Payment within 14 days please.\n\nThanks!\nAda"
    private const val DIGEST =
        "Your weekly digest is here.\n\n12 new issues in otus-mail, 3 regressions. " +
            "Crash-free sessions are at 99.2%.\n\n" +
            "View all issues"
}
