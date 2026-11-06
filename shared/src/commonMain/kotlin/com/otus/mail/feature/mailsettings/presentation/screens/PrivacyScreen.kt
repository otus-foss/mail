package com.otus.mail.feature.mailsettings.presentation.screens

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.pages.components.Subpage
import com.otus.mail.core.messages.domain.DevicePrivacy
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.core.settings.presentation.PRIVACY_TITLE
import com.otus.mail.feature.mailsettings.presentation.components.privacySettings
import com.otus.settings.presentation.components.SettingSections

/**
 * Settings › Privacy & security, as [privacySettings] lists it, with the protections [device]
 * offers; [onOpenAllowed] opens the Allowed senders list.
 */
@Composable
fun PrivacyScreen(
    state: MailBehaviorState,
    onOpenAllowed: () -> Unit,
    onBack: () -> Unit,
    device: DevicePrivacy = DevicePrivacy(),
) {
    Subpage(PRIVACY_TITLE, onBack) {
        SettingSections(privacySettings(state, device) { onOpenAllowed() })
    }
}
