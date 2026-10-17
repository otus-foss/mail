package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.otus.accounts.domain.AccountService
import com.otus.accounts.presentation.screens.AccountSetupScreen
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.ToastMessage
import com.otus.designsystem.elements.ToastAction
import com.otus.mail.feature.folders.domain.sharedFolders
import com.otus.mail.feature.folders.presentation.screens.FoldersScreen
import com.otus.mail.feature.folders.presentation.screens.RuleEditorScreen
import com.otus.mail.feature.folders.presentation.screens.RulesScreen
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.presentation.FolderPillOption
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.InboxPills
import com.otus.mail.feature.inbox.presentation.InboxStatus
import com.otus.mail.feature.inbox.presentation.MailboxSearch
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.SignatureEditorState
import com.otus.mail.feature.inbox.presentation.components.ThemeInboxPreview
import com.otus.mail.feature.inbox.presentation.screens.ActionBarSettingsScreen
import com.otus.mail.feature.inbox.presentation.screens.ComposeScreen
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import com.otus.mail.feature.inbox.presentation.screens.SignatureEditorScreen
import com.otus.mail.feature.inbox.presentation.screens.SignaturesScreen
import com.otus.mail.feature.notifications.presentation.screens.NotificationsScreen
import com.otus.settings.presentation.screens.SettingsScreen
import com.otus.settings.presentation.screens.ThemeSettingsScreen

/**
 * A draft for the page's request; without an account there is nothing to write from. A discarded
 * draft offers Undo, which opens it again, and its saved copy is deleted once the toast leaves.
 */
@Composable
internal fun ComposePage(
    page: MailPage.Compose,
    states: MailStates,
    navigation: MailNavigation,
    onClose: () -> Unit,
) {
    val compose = remember(page) { states.compose(page.request) } ?: return LaunchedEffect(page) { onClose() }
    ComposeScreen(compose, states.behavior.behavior, onClose) { delete ->
        val request = compose.request
        val reopen = ComposeRequest.Resume(compose.draft, (request as? ComposeRequest.Resume)?.from ?: request)
        val undo = ToastAction("Undo") { navigation.go(page.copy(request = reopen)) }
        states.moveToasts.toasts.show(ToastMessage("Draft discarded", undo, onClose = delete))
        onClose()
    }
}

/** Settings › Signatures; tapping an address opens its signature. */
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
    LaunchedEffect(page) { if (signatures.identity(page.identityId) == null) signatures.load() }
    val identity = signatures.identity(page.identityId) ?: return
    val editor = remember(page) { SignatureEditorState(signatures, identity) }
    SignatureEditorScreen(signatures, editor, onBack) {
        states.moveToasts.toasts.show(ToastMessage("Signature copied"))
    }
}
