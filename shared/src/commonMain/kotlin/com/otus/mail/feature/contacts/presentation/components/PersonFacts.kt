package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_cake
import com.otus.designsystem.resources.symbol_call
import com.otus.designsystem.resources.symbol_cloud_off
import com.otus.designsystem.resources.symbol_drive_file_move
import com.otus.designsystem.resources.symbol_group
import com.otus.designsystem.resources.symbol_group_remove
import com.otus.designsystem.resources.symbol_notes
import com.otus.designsystem.shared.contacts.components.ContactEntry
import com.otus.designsystem.shared.contacts.components.ContactHeader
import com.otus.designsystem.shared.contacts.components.ContactValueRow
import com.otus.designsystem.shared.feedback.components.InfoStrip
import com.otus.designsystem.shared.feedback.components.StripAction
import com.otus.designsystem.shared.overlays.components.OptionsItem
import com.otus.designsystem.shared.overlays.components.OptionsMenu
import com.otus.designsystem.shared.pages.components.PageNote
import com.otus.designsystem.shared.rows.components.EntryTaps
import com.otus.designsystem.shared.rows.components.RowGroup
import com.otus.designsystem.shared.rows.elements.SectionCaption
import com.otus.designsystem.theme.tokens.layout.PageTokens
import com.otus.designsystem.theme.tokens.rows.EntryTokens
import com.otus.jscontact.AnniversaryKind
import com.otus.jscontact.PartialDate
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.CardSync
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.domain.ContactValue
import com.otus.mail.feature.contacts.domain.ContactWriteFailure
import com.otus.mail.feature.contacts.presentation.avatar
import com.otus.mail.feature.contacts.presentation.formatPhone
import com.otus.mail.feature.contacts.presentation.groupAvatar
import com.otus.mail.feature.contacts.presentation.icon
import com.otus.mail.feature.contacts.presentation.owner
import com.otus.mail.feature.contacts.presentation.text

/** What one person's page shows besides the header: their books, groups, and a refused change. */
internal class PersonFacts(
    val person: ContactPerson,
    val booksOf: (ContactCard) -> List<ContactBook>,
    val groups: List<ContactCard>,
    val saveElsewhere: ContactBook?,
    /** Whether the person's card holds the avatar of one of the user's own addresses. */
    val ownAvatar: Boolean = false,
)
