package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_content_copy
import com.otus.designsystem.shared.controls.components.SegmentedToggle
import com.otus.designsystem.shared.overlays.components.ClosableSheet
import com.otus.designsystem.shared.overlays.components.SheetConfirm
import com.otus.designsystem.shared.people.components.PersonAvatar
import com.otus.designsystem.shared.selection.components.PickMark
import com.otus.designsystem.shared.selection.components.PickRow
import com.otus.designsystem.theme.ComposeTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.feature.inbox.domain.SendIdentity
import com.otus.mail.feature.inbox.domain.SendIdentityKey
import com.otus.mail.feature.inbox.domain.signature
import com.otus.mail.feature.inbox.presentation.SignaturesState

/**
 * Copies a signature from one other address to [current], or from [current] to several others.
 * Copy from offers only addresses that have a signature. Copy saves, closes, and runs [onCopied].
 */
@Composable
internal fun CopySignatureSheet(
    signatures: SignaturesState,
    current: SendIdentity,
    onDismiss: () -> Unit,
    onCopied: () -> Unit,
) {
    var direction by remember { mutableIntStateOf(FROM) }
    var source by remember { mutableStateOf<SendIdentity?>(null) }
    var targets by remember { mutableStateOf(emptySet<SendIdentityKey>()) }
    val others = signatures.identities.filter { it.key != current.key }
    val ready = if (direction == FROM) source != null else targets.isNotEmpty()
    val copy = {
        if (direction == FROM) {
            source?.let { signatures.copy(it, listOf(current)) }
        } else {
            signatures.copy(current, others.filter { it.key in targets })
        }
        onCopied()
    }
    ClosableSheet("Copy signature", onDismiss, SheetConfirm("Copy", copy, ready, Res.drawable.symbol_content_copy)) {
        SegmentedToggle(listOf("Copy from", "Copy to"), direction, {
            direction = it
        }, Modifier.padding(horizontal = SheetTokens.inset))
        Text(
            if (direction ==
                FROM
            ) {
                "Replaces the signature of ${current.sender.address}."
            } else {
                "Their signatures are replaced with this one."
            },
            Modifier.padding(SheetTokens.inset),
            color = OtusColors.text3,
        )
        others.forEach { identity ->
            val chosen = if (direction == FROM) source?.key == identity.key else identity.key in targets
            SignatureCopyRow(identity, chosen, multiple = direction == TO) {
                if (direction == TO) {
                    targets = if (identity.key in targets) targets - identity.key else targets + identity.key
                } else {
                    source = identity
                }
            }
        }
    }
}

@Composable
private fun SignatureCopyRow(
    identity: SendIdentity,
    chosen: Boolean,
    multiple: Boolean,
    onSelect: () -> Unit,
) {
    val sender = identity.sender
    val preview = identity.signature.preview
    PickRow(
        sender.address,
        preview.ifEmpty { "No signature" },
        PickMark(chosen, multiple = multiple),
        onClick = onSelect.takeIf { multiple || preview.isNotEmpty() },
    ) { PersonAvatar(sender.chipAvatar(), ComposeTokens.dropdownAvatarSize) }
}

private const val FROM = 0
private const val TO = 1
