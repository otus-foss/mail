package com.otusfoss.mail.feature.compose.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_edit_calendar
import com.otusfoss.core.designsystem.resources.symbol_light_mode
import com.otusfoss.core.designsystem.resources.symbol_send
import com.otusfoss.core.designsystem.resources.symbol_wb_twilight
import com.otusfoss.core.designsystem.resources.symbol_work
import com.otusfoss.core.designsystem.shared.dates.components.SpecificDatePicker
import com.otusfoss.core.designsystem.shared.dates.components.TimeWheelPicker
import com.otusfoss.core.designsystem.shared.overlays.components.SheetFrame
import com.otusfoss.core.designsystem.shared.overlays.elements.sheetBodyInset
import com.otusfoss.core.designsystem.shared.rows.components.GroupRow
import com.otusfoss.core.designsystem.shared.rows.components.RowGroup
import com.otusfoss.core.designsystem.theme.tokens.overlays.SheetTokens
import com.otusfoss.core.settings.presentation.components.LocalDateFormatter
import com.otusfoss.mail.feature.compose.domain.SendLaterChoice
import com.otusfoss.mail.feature.compose.domain.earliestPick
import com.otusfoss.mail.feature.compose.domain.pickableTimes
import com.otusfoss.mail.feature.compose.domain.sendLaterChoices
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.yearMonth

/**
 * Picks when the server sends the message: this evening, tomorrow morning, Monday morning, or a
 * date and time from the next five-minute step up to [latest]; earlier times never show.
 * Choosing one runs [onSchedule] with the local time and closes the sheet. With a time already
 * [chosen], Send now clears it.
 */
@Composable
internal fun SendLaterSheet(
    now: LocalDateTime,
    latest: LocalDateTime,
    chosen: LocalDateTime?,
    onSchedule: (LocalDateTime?) -> Unit,
    onDismiss: () -> Unit,
) {
    val earliest = remember(now) { earliestPick(now) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val choose = { at: LocalDateTime? ->
        onDismiss()
        onSchedule(at)
    }
    SheetFrame("Send later", onDismiss, done = null, closeButton = true) {
        Column(
            Modifier.sheetBodyInset(),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            if (chosen != null) RowGroup(listOf { GroupRow(Res.drawable.symbol_send, "Send now", { choose(null) }) })
            SendLaterPresets(now, latest, choose)
            // A limit shorter than the next pickable step leaves nothing to pick.
            if (earliest <= latest) {
                RowGroup(
                    listOf {
                        GroupRow(
                            Res.drawable.symbol_edit_calendar,
                            "Pick date and time",
                            { pickingDate = true },
                            opens = true,
                        )
                    },
                )
            }
        }
    }
    if (pickingDate) {
        SpecificDatePicker(
            earliest.date.yearMonth,
            earliest.date..latest.date,
            onDismiss = { pickingDate = false },
            onConfirm = { date ->
                pickingDate = false
                pickedDate = date
            },
            initialDay = earliest.date.day,
        )
    }
    pickedDate?.let { date ->
        val allowed = pickableTimes(date, earliest, latest)
        TimeWheelPicker("Choose time", MORNING.coerceIn(allowed), allowed, "Schedule", { pickedDate = null }) { time ->
            pickedDate = null
            choose(date.atTime(time))
        }
    }
}

private val SendLaterChoice.label: String
    get() =
        when (this) {
            SendLaterChoice.ThisEvening -> "This evening"
            SendLaterChoice.TomorrowMorning -> "Tomorrow morning"
            SendLaterChoice.MondayMorning -> "Monday morning"
        }

private val SendLaterChoice.symbol
    get() =
        when (this) {
            SendLaterChoice.ThisEvening -> Res.drawable.symbol_wb_twilight
            SendLaterChoice.TomorrowMorning -> Res.drawable.symbol_light_mode
            SendLaterChoice.MondayMorning -> Res.drawable.symbol_work
        }

private const val MORNING_HOUR = 8
private val MORNING = LocalTime(MORNING_HOUR, 0)

/** The send times offered after [now] up to [latest], each sending through [choose]. */
@Composable
private fun SendLaterPresets(
    now: LocalDateTime,
    latest: LocalDateTime,
    choose: (LocalDateTime) -> Unit,
) {
    RowGroup(
        sendLaterChoices(now)
            .filter { it.second <= latest }
            .map { (choice, at) ->
                {
                    GroupRow(
                        choice.symbol,
                        choice.label,
                        { choose(at) },
                        value = LocalDateFormatter.current.sendLaterLabel(at, now),
                    )
                }
            },
    )
}
