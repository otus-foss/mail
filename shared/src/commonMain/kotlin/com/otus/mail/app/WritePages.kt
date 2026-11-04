package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.designsystem.shared.feedback.elements.ToastAction
import com.otus.mail.feature.inbox.domain.SendIdentityKey
import com.otus.mail.feature.inbox.presentation.ComposeExits
import com.otus.mail.feature.inbox.presentation.OutgoingDraft
import com.otus.mail.feature.inbox.presentation.SignatureEditorState
import com.otus.mail.feature.inbox.presentation.reopening
import com.otus.mail.feature.inbox.presentation.screens.ComposeScreen
import com.otus.mail.feature.inbox.presentation.screens.SignatureEditorScreen
import com.otus.mail.feature.inbox.presentation.screens.SignaturesScreen
import kotlinx.datetime.LocalDateTime

/**
 * A draft for the page's request; without an account there is nothing to write from. A discarded
 * draft offers Undo, which opens it again, and its saved copy is deleted once the toast leaves. The
 * toasts can outlive this composition, so they open the draft through [MailStates.pageRequests].
 */
@Composable
internal fun ComposePage(
    page: MailPage.Compose,
    states: MailStates,
    onClose: () -> Unit,
) {
    val compose = remember(page) { states.compose(page.request) } ?: return LaunchedEffect(page) { onClose() }
    val exits =
        remember(compose) {
            object : ComposeExits {
                override fun close() = onClose()

                override fun discarded(delete: () -> Unit) {
                    val undo =
                        ToastAction("Undo") { states.pageRequests.showLater(page.copy(request = compose.reopening())) }
                    states.moveToasts.toasts.show(ToastMessage("Draft discarded", undo, onClose = delete))
                    onClose()
                }

                override fun send(
                    draft: OutgoingDraft,
                    at: LocalDateTime?,
                ) {
                    val outgoing =
                        Outgoing(draft, compose.reopening()) { states.pageRequests.show(page.copy(request = it)) }
                    if (at == null) states.outgoing.send(outgoing) else states.outgoing.schedule(outgoing, at)
                    onClose()
                }
            }
        }
    ComposeScreen(compose, states.behavior.behavior, exits)
}

/** Settings › Mail behavior › Signatures; tapping an address opens its signature. */
@Composable
internal fun SignaturesPage(
    states: MailStates,
    navigation: MailNavigation,
    onBack: () -> Unit,
) {
    SignaturesScreen(
        states.signatures,
        states.behavior.behavior.signatureInReplies,
        onInRepliesChange = { on -> states.behavior.update { it.copy(signatureInReplies = on) } },
        onBack = onBack,
    ) { navigation.go(MailPage.SignatureEditor(it.accountId, it.id)) }
}

/** One identity's signature, once the identities have loaded. */
@Composable
internal fun SignatureEditorPage(
    page: MailPage.SignatureEditor,
    states: MailStates,
    onBack: () -> Unit,
) {
    val signatures = states.signatures
    val key = SendIdentityKey(page.accountId, page.identityId)
    LaunchedEffect(page) { if (signatures.identity(key) == null) signatures.load() }
    val identity = signatures.identity(key) ?: return
    val editor = remember(page) { SignatureEditorState(signatures, identity) }
    SignatureEditorScreen(signatures, editor, onBack) {
        states.moveToasts.toasts.show(ToastMessage("Signature copied"))
    }
}
