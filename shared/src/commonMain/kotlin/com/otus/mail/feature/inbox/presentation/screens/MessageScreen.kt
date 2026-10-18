package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.designsystem.components.ActionBarHost
import com.otus.designsystem.components.ActionDialog
import com.otus.designsystem.components.ChipPlacement
import com.otus.designsystem.components.ContextPanel
import com.otus.designsystem.components.DialogAction
import com.otus.designsystem.elements.IconButton
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_chevron_left
import com.otus.designsystem.resources.symbol_more_horiz
import com.otus.designsystem.theme.InboxTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.PanelTokens
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.BarButton
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.MailAction.SuggestedMove
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageContent
import com.otus.mail.feature.inbox.domain.barLayout
import com.otus.mail.feature.inbox.domain.withContent
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.components.MailActionBar
import com.otus.mail.feature.inbox.presentation.components.MessageBody
import com.otus.mail.feature.inbox.presentation.components.Minimized
import com.otus.mail.feature.inbox.presentation.components.OfferedAction
import com.otus.mail.feature.inbox.presentation.components.everyAction
import com.otus.mail.feature.inbox.presentation.components.offer
import com.otus.mail.feature.inbox.presentation.components.overflow
import com.otus.mail.feature.inbox.presentation.components.rememberConfirmation
import com.otus.mail.feature.inbox.presentation.components.rememberMoveChoice
import com.otus.mail.feature.inbox.presentation.components.rememberSuggestedMove
import com.otus.mail.feature.inbox.presentation.components.replyContext
import com.otus.mail.feature.inbox.presentation.components.replyOffer

/**
 * An open message as a page over the list. With [actions], its bar and menu act on it: an action
 * that takes the message away closes the page first, while marks keep it open and show their new
 * state. Reply, Reply all, Forward, and New mail start a draft through [onCompose]. A [notice],
 * such as a refused mark, shows under the header, since the page hides the list's. [loadContent]
 * fetches the full body, which replies and forwards then quote. Back and the chevron run [onClose].
 */
@Composable
fun MessageScreen(
    message: MailMessage,
    actions: MessageActions?,
    notice: String?,
    loadContent: suspend (MailMessage) -> MessageContent?,
    onCompose: (ComposeRequest) -> Unit,
    onClose: () -> Unit,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        onBackCompleted = onClose,
    )
    val content by produceState<MessageContent?>(null, message.id) { value = loadContent(message) }
    val opened = message.withContent(content)
    val moveChoice = rememberMoveChoice { onClose() }
    val confirmation = rememberConfirmation()
    var menu by remember { mutableStateOf(false) }
    val perform = { action: OfferedAction ->
        if (action.leaves) onClose()
        action.run()
    }
    val role = actions?.cleanup?.role?.invoke(message)
    val behavior = actions?.behavior()
    val layout = behavior?.barLayout(ActionBarPlace.Message, role)
    val suggestion = rememberSuggestedMove(listOf(message), actions, layout?.buttons.orEmpty().contains(SuggestedMove))
    val offerFor = { action: BarButton ->
        actions?.offer(listOf(message), action, moveChoice::choose, suggestion)
            ?: replyOffer(opened, action, role, onCompose)
    }
    val more = behavior?.overflow(ActionBarPlace.Message, offerFor, role).orEmpty()
    Surface(Modifier.fillMaxSize(), color = OtusColors.bgRegular) {
        ActionBarHost(
            bar =
                layout?.let {
                    {
                        MailActionBar(
                            layout,
                            offerFor,
                            perform,
                            Minimized().takeIf { behavior.minimizedBar },
                            behavior.everyAction(ActionBarPlace.Message, offerFor),
                            context = replyContext(message, layout.primary),
                        )
                    }
                },
            Modifier.safeDrawingPadding(),
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(PageTokens.pageInset)) {
                MessageHeader(message, onClose, onMore = { menu = true }.takeIf { more.isNotEmpty() })
                notice?.let { MessageNotice(it) }
                MessageBody(opened, content?.html, padding, Modifier.weight(1f))
            }
        }
    }
    if (menu) MoreActionsDialog(more, { confirmation.run(it, perform) }) { menu = false }
    confirmation.Dialog()
    actions?.let { moveChoice.Sheet(it) }
}

@Composable
private fun MoreActionsDialog(
    actions: List<OfferedAction>,
    onChoose: (OfferedAction) -> Unit,
    onDismiss: () -> Unit,
) {
    ActionDialog("More actions", actions.map { DialogAction(it.label, { onChoose(it) }) }, onDismiss = onDismiss)
}

/** A notice, such as a refused mark, under the header. */
@Composable
private fun MessageNotice(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = PageTokens.textInset),
        color = OtusColors.text2,
        fontSize = InboxTokens.metadataSize,
    )
}

@Composable
private fun MessageHeader(
    message: MailMessage,
    onClose: () -> Unit,
    onMore: (() -> Unit)?,
) {
    ContextPanel(
        placement = ChipPlacement.None,
        navigation = {
            IconButton("Back", onClose, outlined = false) {
                Symbol(Res.drawable.symbol_chevron_left, mirrorInRtl = true, size = PanelTokens.navigationIconSize)
            }
        },
        trailingAction =
            onMore?.let {
                { IconButton("More actions", it) { Symbol(Res.drawable.symbol_more_horiz) } }
            },
    ) {
        Text(
            message.subject.ifBlank { "(No subject)" },
            Modifier.semantics { heading() },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
