package com.otus.mail.feature.folders.presentation.components

import com.otus.avatar.generatedAvatar
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_alternate_email
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.resources.symbol_calendar_month
import com.otus.designsystem.resources.symbol_code
import com.otus.designsystem.resources.symbol_forum
import com.otus.designsystem.resources.symbol_notes
import com.otus.designsystem.resources.symbol_report
import com.otus.designsystem.resources.symbol_schedule
import com.otus.designsystem.resources.symbol_short_text
import com.otus.designsystem.shared.filters.components.RuleChipLine
import com.otus.designsystem.shared.filters.components.RuleChipValue
import com.otus.designsystem.shared.people.components.ChipAvatar
import com.otus.mail.feature.folders.presentation.ChipSlot
import com.otus.mail.feature.folders.presentation.RuleChipText
import com.otus.mail.feature.folders.presentation.RuleIcon
import org.jetbrains.compose.resources.DrawableResource

/** The design system's chip line for [this] condition; people and domains get generated initials. */
fun RuleChipText.chipLine(): RuleChipLine =
    RuleChipLine(
        label,
        RuleChipValue(segments),
        avatar = (slot as? ChipSlot.Avatar)?.chipAvatar,
        icon = (slot as? ChipSlot.Icon)?.icon?.drawable,
    )

/** A whole domain is no single person, so it gets its letter on grey rather than a person's color. */
private val ChipSlot.Avatar.chipAvatar: ChipAvatar
    get() =
        generatedAvatar(initialsFrom, address.orEmpty()).let {
            ChipAvatar(it.initials, if (address == null) GREY_AVATAR else it.colorIndex)
        }

/** The design system's `bgLight` avatar fill. */
private const val GREY_AVATAR = 1

private val RuleIcon.drawable: DrawableResource
    get() =
        when (this) {
            RuleIcon.ShortText -> Res.drawable.symbol_short_text
            RuleIcon.Code -> Res.drawable.symbol_code
            RuleIcon.Forum -> Res.drawable.symbol_forum
            RuleIcon.Notes -> Res.drawable.symbol_notes
            RuleIcon.AttachFile -> Res.drawable.symbol_attach_file
            RuleIcon.Schedule -> Res.drawable.symbol_schedule
            RuleIcon.CalendarMonth -> Res.drawable.symbol_calendar_month
            RuleIcon.Report -> Res.drawable.symbol_report
            RuleIcon.AlternateEmail -> Res.drawable.symbol_alternate_email
        }
