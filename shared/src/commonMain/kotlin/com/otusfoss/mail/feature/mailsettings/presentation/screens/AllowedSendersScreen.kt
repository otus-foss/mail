package com.otusfoss.mail.feature.mailsettings.presentation.screens

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.shared.pages.components.PageNote
import com.otusfoss.core.designsystem.shared.pages.components.Subpage
import com.otusfoss.core.designsystem.shared.rows.elements.RowButton
import com.otusfoss.core.designsystem.shared.settings.components.SettingRow
import com.otusfoss.core.designsystem.shared.settings.components.SettingsSection
import com.otusfoss.mail.core.settings.domain.withoutSender
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState

/**
 * Settings › Privacy & security › Allowed senders: everyone whose remote images always load, added
 * with Always after showing a mail's images. Remove makes a sender ask first again.
 */
@Composable
fun AllowedSendersScreen(
    state: MailBehaviorState,
    onBack: () -> Unit,
) {
    val senders = state.behavior.read.allowedSenders
    Subpage("Allowed senders", onBack) {
        if (senders.isEmpty()) {
            PageNote("No allowed senders yet. Show a mail's images, then tap Always.")
        } else {
            SettingsSection("Images always load from") {
                senders.forEach { address ->
                    SettingRow(address, onClick = null) {
                        RowButton(
                            "Remove",
                            { state.updateRead { it.withoutSender(address) } },
                            accessibilityLabel = "Remove $address",
                        )
                    }
                }
            }
        }
    }
}
