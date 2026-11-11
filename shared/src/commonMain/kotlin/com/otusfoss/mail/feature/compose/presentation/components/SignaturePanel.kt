package com.otusfoss.mail.feature.compose.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.otusfoss.core.designsystem.mail.compose.elements.AddressPill
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_add_photo_alternate
import com.otusfoss.core.designsystem.resources.symbol_content_copy
import com.otusfoss.core.designsystem.resources.symbol_delete
import com.otusfoss.core.designsystem.shared.controls.elements.IconButton
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.core.designsystem.shared.panels.components.ChipPlacement
import com.otusfoss.core.designsystem.shared.panels.components.ContextPanel
import com.otusfoss.core.designsystem.shared.panels.elements.PanelNavigation
import com.otusfoss.core.designsystem.shared.people.components.PersonAvatar
import com.otusfoss.core.designsystem.theme.tokens.editing.SignatureTokens
import com.otusfoss.core.designsystem.theme.tokens.people.SenderTokens
import com.otusfoss.mail.core.messages.presentation.components.chipAvatar
import com.otusfoss.mail.feature.compose.presentation.SignatureEditorState
import com.otusfoss.mail.feature.compose.presentation.SignaturesState
import kotlinx.coroutines.launch

/**
 * The signature page's panel: Back and "Signature" over the address pill, which switches to
 * another address in place, and Add image, Copy, and Remove. Add image shows only where the
 * platform can pick images.
 */
@Composable
internal fun SignaturePanel(
    signatures: SignaturesState,
    editor: SignatureEditorState,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onRemove: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    ContextPanel(
        placement = ChipPlacement.Overlapping,
        navigation = { PanelNavigation("Back", onBack) },
        chips = {
            item(ADDRESS) { AddressChoice(signatures, editor) }
            signatures.images?.let {
                item(IMAGE) {
                    IconButton("Add image", { scope.launch { signatures.pickImage()?.let(editor::addImage) } }) {
                        Symbol(Res.drawable.symbol_add_photo_alternate)
                    }
                }
            }
            item(COPY) { IconButton("Copy signature", onCopy) { Symbol(Res.drawable.symbol_content_copy) } }
            item(REMOVE) { IconButton("Remove signature", onRemove) { Symbol(Res.drawable.symbol_delete) } }
        },
    ) { Text("Signature", Modifier.semantics { heading() }, maxLines = 1) }
}

@Composable
private fun AddressChoice(
    signatures: SignaturesState,
    editor: SignatureEditorState,
) {
    val sender = editor.identity.sender
    var choosing by remember { mutableStateOf(false) }
    Box {
        AddressPill(
            sender.address,
            "Signature of ${sender.address}. Change",
            { choosing = true },
            labelMaxWidth = SignatureTokens.addressLabelMaxWidth,
        ) { PersonAvatar(sender.chipAvatar, SenderTokens.avatarSize) }
        if (choosing) {
            FromDropdown(
                signatures.identities,
                signatures.accountList,
                recent = emptyList(),
                selected = sender,
                onSelect = editor::switchTo,
                onDismiss = { choosing = false },
            )
        }
    }
}

private const val ADDRESS = "address"
private const val IMAGE = "image"
private const val COPY = "copy"
private const val REMOVE = "remove"
