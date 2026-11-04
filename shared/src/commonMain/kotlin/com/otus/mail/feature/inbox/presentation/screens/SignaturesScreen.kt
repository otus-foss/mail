package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.otus.designsystem.shared.pages.components.Subpage
import com.otus.designsystem.shared.people.components.PersonAvatar
import com.otus.designsystem.shared.people.components.PersonRow
import com.otus.designsystem.shared.selection.components.ListCaption
import com.otus.designsystem.shared.settings.components.SettingToggleRow
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.tokens.editing.ComposeTokens
import com.otus.designsystem.theme.tokens.layout.PageTokens
import com.otus.mail.feature.inbox.domain.SendIdentity
import com.otus.mail.feature.inbox.domain.signature
import com.otus.mail.feature.inbox.presentation.SignaturesState
import com.otus.mail.feature.inbox.presentation.components.chipAvatar

/**
 * Settings › Mail behavior › Signatures: whether replies get a signature, then every address by
 * account with the start of its signature. Tapping one runs [onEdit].
 */
@Composable
fun SignaturesScreen(
    state: SignaturesState,
    inReplies: Boolean,
    onInRepliesChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onEdit: (SendIdentity) -> Unit,
) {
    LaunchedEffect(state) { state.load() }
    Subpage("Signatures", onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap)) {
            SettingToggleRow("Add to replies", inReplies, onInRepliesChange)
            state.notice?.let { Text(it, color = OtusColors.text2) }
            state.accountList.forEach { account ->
                val identities = state.identities.filter { it.accountId == account.id }
                if (identities.isNotEmpty()) {
                    ListCaption(account.name)
                    identities.forEach { identity -> SignatureRow(identity) { onEdit(identity) } }
                }
            }
        }
    }
}

@Composable
private fun SignatureRow(
    identity: SendIdentity,
    onClick: () -> Unit,
) {
    val sender = identity.sender
    val preview = identity.signature.preview
    PersonRow(sender.address, preview.ifEmpty { "No signature" }, onClick, missing = preview.isEmpty()) {
        PersonAvatar(chipAvatar(sender.avatar, sender.name, sender.address), ComposeTokens.dropdownAvatarSize)
    }
}
