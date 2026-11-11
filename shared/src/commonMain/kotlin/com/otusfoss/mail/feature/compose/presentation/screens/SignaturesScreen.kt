package com.otusfoss.mail.feature.compose.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.otusfoss.core.designsystem.shared.pages.components.Subpage
import com.otusfoss.core.designsystem.shared.people.components.PersonAvatar
import com.otusfoss.core.designsystem.shared.people.components.PersonRow
import com.otusfoss.core.designsystem.shared.rows.elements.Caption
import com.otusfoss.core.designsystem.shared.rows.elements.listCaptionInset
import com.otusfoss.core.designsystem.shared.settings.components.SettingToggleRow
import com.otusfoss.core.designsystem.theme.colors.OtusColors
import com.otusfoss.core.designsystem.theme.tokens.layout.PageTokens
import com.otusfoss.core.designsystem.theme.tokens.people.AvatarTokens
import com.otusfoss.mail.core.messages.domain.SendIdentity
import com.otusfoss.mail.core.messages.domain.signature
import com.otusfoss.mail.core.messages.presentation.components.chipAvatar
import com.otusfoss.mail.feature.compose.presentation.SignaturesState

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
                    Caption(account.name, Modifier.listCaptionInset())
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
        PersonAvatar(chipAvatar(sender.avatar, sender.name, sender.address), AvatarTokens.menuSize)
    }
}
