package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.designsystem.components.ActionBarHost
import com.otus.designsystem.components.ConfirmDialog
import com.otus.designsystem.components.FormattingBar
import com.otus.designsystem.components.ImagePreset
import com.otus.designsystem.components.ImageSizing
import com.otus.designsystem.components.ResizableImage
import com.otus.designsystem.elements.RichTextBody
import com.otus.designsystem.elements.rememberAvatarImage
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.PanelTokens
import com.otus.designsystem.theme.SignatureTokens
import com.otus.mail.feature.inbox.domain.SignatureImage
import com.otus.mail.feature.inbox.domain.SignatureImageSize
import com.otus.mail.feature.inbox.presentation.SignatureEditorState
import com.otus.mail.feature.inbox.presentation.SignaturesState
import com.otus.mail.feature.inbox.presentation.components.CopySignatureSheet
import com.otus.mail.feature.inbox.presentation.components.LinkDialog
import com.otus.mail.feature.inbox.presentation.components.SignaturePanel
import kotlin.io.encoding.Base64

/**
 * Edits one identity's signature: the panel switches the address in place, adds an image, copies
 * signatures, or removes this one after asking. The text and its images sit in a box below; the
 * formatting row shows with the keyboard. Edits save as they happen. [onCopied] runs after a copy.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignatureEditorScreen(
    signatures: SignaturesState,
    editor: SignatureEditorState,
    onBack: () -> Unit,
    onCopied: () -> Unit,
) {
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onBack)
    LaunchedEffect(editor) { editor.follow() }
    DisposableEffect(editor) { onDispose { editor.close() } }
    var copying by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }
    val formattingBar: @Composable () -> Unit = { FormattingBar(editor.text, onLink = { linking = true }) }
    Surface(Modifier.fillMaxSize(), color = OtusColors.bgRegular) {
        ActionBarHost(
            bar = formattingBar.takeIf { WindowInsets.isImeVisible },
            Modifier.safeDrawingPadding(),
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(
                        PageTokens.pageInset,
                    ).verticalScroll(rememberScrollState())
                    .padding(padding),
            ) {
                SignaturePanel(
                    signatures,
                    editor,
                    onBack = onBack,
                    onCopy = { copying = true },
                    onRemove = { removing = true },
                )
                SignatureBox(editor)
            }
        }
    }
    if (copying) {
        CopySignatureSheet(signatures, editor.identity, onDismiss = { copying = false }) {
            editor.switchTo(signatures.identity(editor.identity.id) ?: editor.identity)
            onCopied()
        }
    }
    if (removing) {
        ConfirmDialog(
            "Remove signature?",
            "Messages from ${editor.identity.sender.address} go out without one.",
            "Remove",
            onConfirm = editor::remove,
            onDismiss = { removing = false },
        )
    }
    if (linking) LinkDialog(editor.text::addLink) { linking = false }
}

/** The text and the images under it, in one `bgDark` box with a neutral outline, focused or not. */
@Composable
private fun SignatureBox(editor: SignatureEditorState) {
    val shape = RoundedCornerShape(SignatureTokens.editorRadius)
    Column(
        Modifier
            .padding(top = PageTokens.contentInset)
            .fillMaxWidth()
            .background(OtusColors.bgDark, shape)
            .border(PanelTokens.borderWidth, OtusColors.strokeBgDark, shape)
            .padding(bottom = PageTokens.contentInset),
        verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap),
    ) {
        RichTextBody(editor.text, placeholder = "Write your signature")
        editor.images.forEachIndexed { index, image ->
            SignatureImageView(editor, index, image)
        }
    }
}

@Composable
private fun SignatureImageView(
    editor: SignatureEditorState,
    index: Int,
    image: SignatureImage,
) {
    val bitmap = rememberAvatarImage(remember(image.base64) { Base64.decode(image.base64) }) ?: return
    ResizableImage(
        bitmap,
        ImageSizing(image.width.dp, presets) { editor.resize(index, it.value.toInt()) },
        selected = editor.selected == index,
        onSelect = { editor.selected = index },
        onRemove = { editor.removeImage(index) },
        Modifier.padding(horizontal = PageTokens.textInset),
    )
}

private val presets = SignatureImageSize.entries.map { ImagePreset(it.label, it.width.dp) }
