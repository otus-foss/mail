package com.otus.mail.feature.mailsettings.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.otus.designsystem.shared.pages.components.Subpage
import com.otus.mail.core.settings.presentation.FolderPillOption
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.core.settings.presentation.SidebarPinOption
import com.otus.mail.feature.mailsettings.presentation.components.BehaviorPages
import com.otus.mail.feature.mailsettings.presentation.components.mailBehaviorSettings
import com.otus.settings.presentation.components.SettingSections

/**
 * Settings › Mail behavior, as [mailBehaviorSettings] lists it; [pages] open the pages its rows
 * lead to.
 */
@Composable
fun MailBehaviorScreen(
    state: MailBehaviorState,
    folderPills: List<FolderPillOption>,
    startPages: List<SidebarPinOption>,
    pages: BehaviorPages,
    onBack: () -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Subpage("Mail behavior", onBack) {
        SettingSections(mailBehaviorSettings(state, folderPills, startPages, rtl, pages))
    }
}
