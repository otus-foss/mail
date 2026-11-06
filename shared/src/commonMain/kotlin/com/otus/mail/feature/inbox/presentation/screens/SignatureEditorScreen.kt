package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.designsystem.mail.compose.components.EditableImage
import com.otus.designsystem.mail.compose.components.FormattingBar
import com.otus.designsystem.mail.compose.components.ResizableImage
import com.otus.designsystem.mail.compose.components.RichTextBody
import com.otus.designsystem.shared.actionbar.components.ActionBarHost
import com.otus.designsystem.shared.actionbar.components.BarMorph
import com.otus.designsystem.shared.input.elements.isKeyboardUp
import com.otus.designsystem.shared.overlays.components.ConfirmDialog
import com.otus.designsystem.shared.pages.components.PageNote
import com.otus.designsystem.shared.pages.components.belowControlPanel
import com.otus.designsystem.shared.pages.components.pageSideInset
import com.otus.designsystem.shared.people.elements.rememberAvatarImage
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.shapes.SmoothCornerShape
import com.otus.designsystem.theme.shapes.strokeBorder
import com.otus.designsystem.theme.tokens.core.Sizes
import com.otus.designsystem.theme.tokens.editing.SignatureTokens
import com.otus.designsystem.theme.tokens.layout.PageTokens
import com.otus.mail.core.messages.domain.SignatureImage
import com.otus.mail.feature.inbox.presentation.SignatureEditorState
import com.otus.mail.feature.inbox.presentation.SignaturesState
import com.otus.mail.feature.inbox.presentation.components.CopySignatureSheet
import com.otus.mail.feature.inbox.presentation.components.LinkSheet
import com.otus.mail.feature.inbox.presentation.components.SignatureImageBar
import com.otus.mail.feature.inbox.presentation.components.SignaturePanel
import com.otus.mail.feature.inbox.presentation.components.imageSizing
import kotlin.io.encoding.Base64

/**
 * Edits one identity's signature: the panel switches the address in place, adds an image, copies
 * signatures, or removes this one after asking. The text and its images sit in a box below; the
 * formatting row shows with the keyboard. Edits save as they happen. [onCopied] runs after a copy.
 */
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
    val keyboardUp = isKeyboardUp()
    val shown =
        when {
            editor.selected != null -> SignatureBar.Image
            keyboardUp -> SignatureBar.Formatting
            else -> null
        }
    Surface(Modifier.fillMaxSize(), color = OtusColors.bgRegular) {
        ActionBarHost(
            bar = shown?.let { { SignatureBottom(editor, it) { linking = true } } },
            Modifier.belowControlPanel(),
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding),
            ) {
                SignaturePanel(
                    signatures,
                    editor,
                    onBack = onBack,
                    onCopy = { copying = true },
                    onRemove = { removing = true },
                )
                Column(Modifier.pageSideInset()) {
                    signatures.notice?.let { Box(Modifier.padding(top = PageTokens.contentInset)) { PageNote(it) } }
                    SignatureBox(editor)
                }
            }
        }
    }
    if (copying) {
        CopySignatureSheet(signatures, editor.identity, onDismiss = { copying = false }) {
            editor.reload(signatures.identity(editor.identity.key) ?: editor.identity)
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
    if (linking) LinkSheet(editor.text::addLink) { linking = false }
}

/** The text and the images under it, in one `bgDark` box with a neutral outline, focused or not. */
@Composable
private fun SignatureBox(editor: SignatureEditorState) {
    val shape = SmoothCornerShape(SignatureTokens.editorRadius)
    Column(
        Modifier
            .padding(top = PageTokens.contentInset)
            .fillMaxWidth()
            .background(OtusColors.bgDark, shape)
            .strokeBorder(Sizes.borderWidth, OtusColors.strokeBgDark, shape)
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
    val focus = LocalFocusManager.current
    ResizableImage(
        EditableImage(remember(bitmap) { BitmapPainter(bitmap) }, "Signature image"),
        editor.imageSizing(index, image),
        selected = editor.selected == index,
        onSelect = {
            focus.clearFocus()
            editor.selected = index
        },
        Modifier.padding(horizontal = PageTokens.textInset),
    )
}

/** The image bar or the formatting row, each morphing into the other. */
@Composable
private fun SignatureBottom(
    editor: SignatureEditorState,
    shown: SignatureBar,
    onLink: () -> Unit,
) {
    BarMorph(shown) { bar ->
        when (bar) {
            SignatureBar.Image -> SignatureImageBar(editor)
            SignatureBar.Formatting -> FormattingBar(editor.text, onLink = onLink)
        }
    }
}

/** Which bar the signature editor shows: the image's while one is selected, the formatting row while typing. */
private enum class SignatureBar { Image, Formatting }
