package com.otusfoss.mail.feature.mailsettings.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.otusfoss.core.designsystem.shared.pages.components.Subpage
import com.otusfoss.core.settings.presentation.components.SettingSections
import com.otusfoss.mail.core.settings.presentation.FolderPillOption
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.core.settings.presentation.SidebarPinOption
import com.otusfoss.mail.feature.mailsettings.presentation.components.BehaviorPages
import com.otusfoss.mail.feature.mailsettings.presentation.components.mailBehaviorSettings

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
    Subpage(MAIL_BEHAVIOR_TITLE, onBack) {
        SettingSections(mailBehaviorSettings(state, folderPills, startPages, rtl, pages))
    }
}

/** The page's title, which the settings home and search also show. */
const val MAIL_BEHAVIOR_TITLE = "Mail behavior"
