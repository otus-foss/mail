package com.otusfoss.mail.feature.mailsettings.presentation.screens

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.shared.pages.components.Subpage
import com.otusfoss.core.settings.presentation.components.SettingSections
import com.otusfoss.mail.core.messages.domain.DevicePrivacy
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.core.settings.presentation.PRIVACY_TITLE
import com.otusfoss.mail.feature.mailsettings.presentation.components.privacySettings

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
