package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_bookmark
import com.otusfoss.core.designsystem.resources.symbol_filter_list
import com.otusfoss.core.designsystem.resources.symbol_inbox
import com.otusfoss.core.designsystem.resources.symbol_menu
import com.otusfoss.core.designsystem.resources.symbol_tune
import com.otusfoss.core.settings.presentation.components.ChoiceRowSpec
import com.otusfoss.core.settings.presentation.components.PageRowSpec
import com.otusfoss.core.settings.presentation.components.SettingRowSpec
import com.otusfoss.core.settings.presentation.components.SettingsSectionSpec
import com.otusfoss.core.settings.presentation.components.ToggleRowSpec
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.settings.presentation.FolderPillOption
import com.otusfoss.mail.core.settings.presentation.InboxPills
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.core.settings.presentation.SidebarPinOption
import com.otusfoss.mail.core.settings.presentation.sectionSummary
import com.otusfoss.mail.feature.mailsettings.presentation.inboxSummary

/** The pages Mail behavior's rows open, at their top for null or with the named setting found. */
class BehaviorPages(
    val sidebar: (setting: String?) -> Unit,
    val pinned: (setting: String?) -> Unit,
    val filterPanel: (setting: String?) -> Unit,
    val actionBar: (setting: String?) -> Unit,
    val signatures: (setting: String?) -> Unit,
)
