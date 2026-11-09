package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.SendIdentityKey
import com.otus.mail.feature.compose.presentation.ComposeExits
import com.otus.mail.feature.compose.presentation.Outgoing
import com.otus.mail.feature.compose.presentation.OutgoingDraft
import com.otus.mail.feature.compose.presentation.SignatureEditorState
import com.otus.mail.feature.compose.presentation.reopening
import com.otus.mail.feature.compose.presentation.screens.ComposeScreen
import com.otus.mail.feature.compose.presentation.screens.SignatureEditorScreen
import com.otus.mail.feature.compose.presentation.screens.SignaturesScreen
import kotlinx.datetime.LocalDateTime

/**
 * A draft for the page's request; without an account there is nothing to write from. A discarded
 * draft offers Undo, which opens it again, and its saved copy is deleted once the toast leaves. The
 * toasts can outlive this composition, so they open the draft through [MailStates.requests].
 */
@Composable
internal fun ComposePage(
    page: MailPage.Compose,
    states: MailStates,
    onClose: () -> Unit,
) {
    val compose = remember(page) { states.compose.draft(page.request) } ?: return LaunchedEffect(page) { onClose() }
    val resumed = page.request as? ComposeRequest.Resume
    val exits =
        remember(compose) {
            object : ComposeExits {
                override fun close() = onClose()

                override fun discarded(delete: () -> Unit) {
                    val pages = states.requests.pages
                    val undo = UiAction("Undo") { pages.showLater(page.copy(request = compose.reopening())) }
                    val remove = { states.compose.discard(resumed?.held, delete) }
                    states.moveToasts.toasts.show(ToastMessage("Draft discarded", undo, onClose = remove))
                    onClose()
                }

                override fun send(
                    draft: OutgoingDraft,
                    at: LocalDateTime?,
                ) {
                    val outgoing =
                        Outgoing(
                            draft,
                            compose.reopening(),
                            resumed?.sendAt,
                            filed = { states.list.refreshPages(keepReport = true) },
                        ) { states.requests.pages.show(page.copy(request = it)) }
                    states.compose.send(outgoing, at)
                    onClose()
                }
            }
        }
    ComposeScreen(compose, states.behavior.behavior.actions, states.behavior.behavior.write.recentFrom, exits)
}

/** Settings › Mail behavior › Signatures; tapping an address opens its signature. */
@Composable
internal fun SignaturesPage(
    states: MailStates,
    navigation: MailNavigation,
    onBack: () -> Unit,
) {
    SignaturesScreen(
        states.compose.signatures,
        states.behavior.behavior.write.signatureInReplies,
        onInRepliesChange = { on -> states.behavior.updateWrite { it.copy(signatureInReplies = on) } },
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
    val signatures = states.compose.signatures
    val key = SendIdentityKey(page.accountId, page.identityId)
    LaunchedEffect(page) { if (signatures.identity(key) == null) signatures.load() }
    val identity = signatures.identity(key) ?: return
    val editor = remember(page) { SignatureEditorState(signatures, identity) }
    SignatureEditorScreen(signatures, editor, onBack) {
        states.moveToasts.toasts.show(ToastMessage("Signature copied"))
    }
}
