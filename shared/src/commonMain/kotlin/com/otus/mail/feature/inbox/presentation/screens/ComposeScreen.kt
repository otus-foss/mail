package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.designsystem.components.ActionBarHost
import com.otus.designsystem.components.ChipPlacement
import com.otus.designsystem.components.ChoiceOptions
import com.otus.designsystem.components.ChoiceSheet
import com.otus.designsystem.components.ConfirmDialog
import com.otus.designsystem.components.ContextPanel
import com.otus.designsystem.elements.ComposeBody
import com.otus.designsystem.elements.IconButton
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_chevron_left
import com.otus.designsystem.theme.InboxTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.PanelTokens
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.presentation.ComposeState
import com.otus.mail.feature.inbox.presentation.components.ComposeBar
import com.otus.mail.feature.inbox.presentation.components.ComposeFields

/**
 * A new message, reply, or forward as a page. Leaving it by Back or the chevron runs [onClose]
 * and saves the draft if the user wrote anything. Discard asks first when there is something
 * to lose, then deletes the saved copy and closes. A failed save shows under the header.
 */
@Composable
fun ComposeScreen(
    state: ComposeState,
    behavior: MailBehavior,
    onClose: () -> Unit,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        onBackCompleted = onClose,
    )
    LaunchedEffect(state) { state.loadIdentities() }
    // Leaving the page by any route, Back or another page, saves what is new.
    DisposableEffect(state) { onDispose { state.close() } }
    var confirming by remember { mutableStateOf(false) }
    var choosingFrom by remember { mutableStateOf(false) }
    val discard = {
        state.discard()
        onClose()
    }
    Surface(Modifier.fillMaxSize(), color = OtusColors.bgRegular) {
        ActionBarHost(
            bar = {
                ComposeBar(behavior.barLayout(ActionBarPlace.Compose), state.draft, onDiscard = {
                    if (state.edited) confirming = true else discard()
                })
            },
            Modifier.safeDrawingPadding(),
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(PageTokens.pageInset)) {
                ComposeHeader(state.request.title, onClose)
                state.notice?.let {
                    Text(
                        it,
                        Modifier.padding(horizontal = PageTokens.textInset),
                        color = OtusColors.text2,
                        fontSize = InboxTokens.metadataSize,
                    )
                }
                ComposeContent(
                    state,
                    { choosingFrom = true }.takeIf { state.identities.size > 1 },
                    Modifier.weight(1f),
                    padding,
                )
            }
        }
    }
    if (confirming) {
        ConfirmDialog(
            "Discard draft?",
            "The message and its saved copy in Drafts are deleted.",
            "Discard",
            onConfirm = discard,
            onDismiss = { confirming = false },
        )
    }
    if (choosingFrom) FromSheet(state) { choosingFrom = false }
}

/** The header lines, then the body filling the rest of the page; both scroll together. */
@Composable
private fun ComposeContent(
    state: ComposeState,
    onChooseFrom: (() -> Unit)?,
    modifier: Modifier,
    padding: PaddingValues,
) {
    val focus = remember { FocusRequester() }
    var fieldsHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // A reply starts in its quoted body, so only new messages and forwards start on To.
    LaunchedEffect(focus) { if (state.request !is ComposeRequest.Reply) focus.requestFocus() }
    BoxWithConstraints(modifier) {
        val bodyHeight = maxHeight - with(density) { fieldsHeight.toDp() } - padding.calculateBottomPadding()

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)) {
            ComposeFields(state, onChooseFrom, focus, Modifier.onSizeChanged { fieldsHeight = it.height })
            ComposeBody(
                state.draft.body,
                { body -> state.edit { it.copy(body = body) } },
                placeholder = "Message",
                Modifier.heightIn(min = bodyHeight.coerceAtLeast(0.dp)),
            )
        }
    }
}

@Composable
private fun ComposeHeader(
    title: String,
    onClose: () -> Unit,
) {
    ContextPanel(
        placement = ChipPlacement.None,
        navigation = {
            IconButton("Back", onClose, outlined = false) {
                Symbol(Res.drawable.symbol_chevron_left, mirrorInRtl = true, size = PanelTokens.navigationIconSize)
            }
        },
    ) {
        Text(title, Modifier.semantics { heading() }, maxLines = 1)
    }
}

@Composable
private fun FromSheet(
    state: ComposeState,
    onDismiss: () -> Unit,
) {
    val identities = state.identities
    ChoiceSheet(
        "Send from",
        ChoiceOptions(identities, selected = state.draft.from ?: identities.first()),
        label = { it.address },
        onSelect = { identity -> state.edit { it.copy(from = identity) } },
        onDismiss = onDismiss,
        detail = { it.name.takeIf(String::isNotBlank) },
    )
}

private val ComposeRequest.title: String
    get() =
        when (this) {
            is ComposeRequest.New -> "New message"
            is ComposeRequest.Reply -> if (all) "Reply all" else "Reply"
            is ComposeRequest.Forward -> "Forward"
        }
