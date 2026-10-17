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
import com.otus.designsystem.components.ClosableSheet
import com.otus.designsystem.components.PersonAvatar
import com.otus.designsystem.components.PickMark
import com.otus.designsystem.components.PickRow
import com.otus.designsystem.components.SegmentedToggle
import com.otus.designsystem.components.SheetConfirm
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_content_copy
import com.otus.designsystem.theme.ComposeTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.feature.inbox.domain.SendIdentity
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
    var targets by remember { mutableStateOf(emptySet<String>()) }
    val others = signatures.identities.filter { it.id != current.id }
    val ready = if (direction == FROM) source != null else targets.isNotEmpty()
    val copy = {
        if (direction == FROM) {
            source?.let { signatures.copy(it, listOf(current)) }
        } else {
            signatures.copy(current, others.filter { it.id in targets })
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
            val sender = identity.sender
            val preview = identity.signature.preview
            val chosen = if (direction == FROM) source?.id == identity.id else identity.id in targets
            PickRow(
                sender.address,
                preview.ifEmpty { "No signature" },
                PickMark(chosen, multiple = direction == TO),
                onClick =
                    when {
                        direction == TO -> {
                            { targets = if (identity.id in targets) targets - identity.id else targets + identity.id }
                        }

                        preview.isNotEmpty() -> {
                            { source = identity }
                        }

                        else -> {
                            null
                        }
                    },
            ) { PersonAvatar(sender.chipAvatar(), ComposeTokens.dropdownAvatarSize) }
        }
    }
}

private const val FROM = 0
private const val TO = 1
