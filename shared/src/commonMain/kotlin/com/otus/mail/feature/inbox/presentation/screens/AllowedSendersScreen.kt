package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.PageNote
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.components.SettingsSection
import com.otus.designsystem.components.Subpage
import com.otus.designsystem.elements.RowButton
import com.otus.mail.feature.inbox.domain.withoutSender
import com.otus.mail.feature.inbox.presentation.MailBehaviorState

/**
 * Settings › Privacy › Allowed senders: everyone whose remote images always load, added with
 * "Always for" on a mail with blocked images. Remove makes a sender ask first again.
 */
@Composable
fun AllowedSendersScreen(
    state: MailBehaviorState,
    onBack: () -> Unit,
) {
    val senders = state.behavior.allowedSenders
    Subpage("Allowed senders", onBack) {
        if (senders.isEmpty()) {
            PageNote("No allowed senders yet. Tap Always for a sender on a mail with blocked images.")
        } else {
            SettingsSection("Images always load from") {
                senders.forEach { address ->
                    SettingRow(address, onClick = null) {
                        RowButton(
                            "Remove",
                            { state.update { it.withoutSender(address) } },
                            accessibilityLabel = "Remove $address",
                        )
                    }
                }
            }
        }
    }
}
