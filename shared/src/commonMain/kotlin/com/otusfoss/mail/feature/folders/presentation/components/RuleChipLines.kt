package com.otusfoss.mail.feature.folders.presentation.components

import com.otusfoss.core.avatar.generatedAvatar
import com.otusfoss.core.designsystem.mail.folders.components.RuleChipLine
import com.otusfoss.core.designsystem.mail.folders.components.RuleChipValue
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_alternate_email
import com.otusfoss.core.designsystem.resources.symbol_attach_file
import com.otusfoss.core.designsystem.resources.symbol_calendar_month
import com.otusfoss.core.designsystem.resources.symbol_code
import com.otusfoss.core.designsystem.resources.symbol_forum
import com.otusfoss.core.designsystem.resources.symbol_notes
import com.otusfoss.core.designsystem.resources.symbol_report
import com.otusfoss.core.designsystem.resources.symbol_schedule
import com.otusfoss.core.designsystem.resources.symbol_short_text
import com.otusfoss.core.designsystem.shared.people.components.ChipAvatar
import com.otusfoss.mail.feature.folders.presentation.ChipSlot
import com.otusfoss.mail.feature.folders.presentation.RuleChipText
import com.otusfoss.mail.feature.folders.presentation.RuleIcon
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
