package com.otusfoss.mail.feature.read.presentation.components

import com.otusfoss.core.designsystem.shared.feedback.components.ToastState
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.MessageContent
import com.otusfoss.mail.core.messages.domain.MessageHtml
import com.otusfoss.mail.core.messages.domain.OwnAddresses
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.core.messages.domain.RemoteImages
import com.otusfoss.mail.feature.read.domain.ConversationMessages
import com.otusfoss.mail.feature.read.domain.KnownPeople
import com.otusfoss.mail.feature.read.presentation.NewsletterSteps
import com.otusfoss.mail.feature.read.presentation.PageBody
import com.otusfoss.mail.feature.read.presentation.ReadContext
import com.otusfoss.mail.feature.read.presentation.ReadDisplay
import com.otusfoss.mail.feature.read.presentation.ReadEffects
import com.otusfoss.mail.feature.read.presentation.ReadFiles
import com.otusfoss.mail.feature.read.presentation.ReadFolders
import com.otusfoss.mail.feature.read.presentation.ReadLoads
import com.otusfoss.mail.feature.read.presentation.ReadPrivacy
import com.otusfoss.mail.feature.read.presentation.ReadReplies
import com.otusfoss.mail.feature.read.presentation.ReadSender
import com.otusfoss.mail.feature.read.presentation.ReadServices
import com.otusfoss.mail.feature.read.presentation.ReadState
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A tap on a blocked image's placeholder in the Read view, with Load images set to Never. */
class PlaceholderTapTest {
    private val toasts = ToastState()
    private val page =
        MailMessage(
            "ada/e1",
            MailSender("Grace", "grace@example.org"),
            "Plans",
            "",
            ReceivedAt("2026-10-01", "09:00"),
            MailLocation("Inbox", "Ada", mailboxId = "a", mailboxIds = setOf("a")),
        )
    private val body = PageBody.Loaded(MessageContent("", false, MessageHtml("<img src=x>", remoteContent = true)))
    private val state = ReadState(page)
    private val look = ReadPageLook(context(), state, page, emptySet(), emptyMap())

    private fun tapPlaceholder() = look.images(page, body).onPlaceholder!!.invoke()

    @Test
    fun aPlaceholderTapDoesNotShowTheImages() {
        tapPlaceholder()
        assertFalse(state.bodies.imagesShown(page))
        assertFalse(look.images(page, body).load)
    }

    @Test
    fun aPlaceholderTapAsksInAToast() {
        tapPlaceholder()
        val toast = toasts.messages.single()
        assertEquals("Images blocked", toast.text)
        assertEquals("Show", toast.action?.label)
    }

    @Test
    fun repeatedTapsKeepOneToast() {
        repeat(3) { tapPlaceholder() }
        assertEquals(1, toasts.messages.size)
    }

    @Test
    fun theToastsShowLoadsTheImages() {
        tapPlaceholder()
        toasts.messages
            .single()
            .action!!
            .onClick()
        assertTrue(state.bodies.imagesShown(page))
        assertTrue(look.images(page, body).load)
    }

    private fun context() =
        ReadContext(
            ReadLoads(ConversationMessages { listOf(it) }, content = { flowOf(null) }),
            ReadFolders(of = { emptyList() }, cameFrom = { emptySet() }, newFolder = null),
            ReadPrivacy(
                { RemoteImages.Never },
                allowed = { emptyList() },
                known = KnownPeople { emptySet() },
                allowSender = {},
            ),
            ReadServices(null, null, null, null, ReadFiles(null, null, null), null, ReadSender(null)),
            ReadEffects(toasts, launch = {}, NewsletterSteps()),
            ReadDisplay(showQuotes = { false }, zen = { true }, setZen = {}),
            ReadReplies(OwnAddresses(emptyMap()), onCompose = {}),
        )
}
