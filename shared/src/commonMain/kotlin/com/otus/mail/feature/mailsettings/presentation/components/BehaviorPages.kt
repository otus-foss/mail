package com.otus.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_menu
import com.otus.designsystem.resources.symbol_tune
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.settings.presentation.FolderPillOption
import com.otus.mail.core.settings.presentation.InboxPills
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.core.settings.presentation.SidebarPinOption
import com.otus.mail.core.settings.presentation.sectionSummary
import com.otus.mail.feature.mailsettings.presentation.inboxSummary
import com.otus.settings.presentation.components.ChoiceRowSpec
import com.otus.settings.presentation.components.PageRowSpec
import com.otus.settings.presentation.components.SettingRowSpec
import com.otus.settings.presentation.components.SettingsSectionSpec
import com.otus.settings.presentation.components.ToggleRowSpec

/** The pages Mail behavior's rows open, at their top for null or with the named setting found. */
class BehaviorPages(
    val sidebar: (setting: String?) -> Unit,
    val pinned: (setting: String?) -> Unit,
    val filterPanel: (setting: String?) -> Unit,
    val actionBar: (setting: String?) -> Unit,
    val signatures: (setting: String?) -> Unit,
)
