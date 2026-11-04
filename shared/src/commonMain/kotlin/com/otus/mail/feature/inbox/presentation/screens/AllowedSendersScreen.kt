package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.pages.components.PageNote
import com.otus.designsystem.shared.pages.components.Subpage
import com.otus.designsystem.shared.rows.elements.RowButton
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.components.SettingsSection
import com.otus.mail.feature.inbox.domain.withoutSender
import com.otus.mail.feature.inbox.presentation.MailBehaviorState

/**
 * Settings › Privacy & security › Allowed senders: everyone whose remote images always load, added
 * with Always after showing a mail's images. Remove makes a sender ask first again.
 */
@Composable
fun AllowedSendersScreen(
    state: MailBehaviorState,
    onBack: () -> Unit,
) {
    val senders = state.behavior.allowedSenders
    Subpage("Allowed senders", onBack) {
        if (senders.isEmpty()) {
            PageNote("No allowed senders yet. Show a mail's images, then tap Always.")
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
