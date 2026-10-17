package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ClosableSheet
import com.otus.designsystem.components.GroupRow
import com.otus.designsystem.components.RowGroup
import com.otus.designsystem.components.SpecificDatePicker
import com.otus.designsystem.components.TimeWheelPicker
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_edit_calendar
import com.otus.designsystem.resources.symbol_light_mode
import com.otus.designsystem.resources.symbol_send
import com.otus.designsystem.resources.symbol_wb_twilight
import com.otus.designsystem.resources.symbol_work
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.feature.inbox.domain.SendLaterChoice
import com.otus.mail.feature.inbox.domain.sendLaterChoices
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.yearMonth

/**
 * Picks when the server sends the message: this evening, tomorrow morning, Monday morning, or a
 * date and time, up to [latest]. Choosing one runs [onSchedule] with the local time and closes the
 * sheet. With a time already [chosen], Send now clears it.
 */
@Composable
internal fun SendLaterSheet(
    now: LocalDateTime,
    latest: LocalDateTime,
    chosen: LocalDateTime?,
    onSchedule: (LocalDateTime?) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickingDate by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val choose = { at: LocalDateTime? ->
        onDismiss()
        onSchedule(at)
    }
    ClosableSheet("Send later", onDismiss) {
        Column(
            Modifier.padding(horizontal = SheetTokens.inset),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            if (chosen != null) RowGroup(listOf { GroupRow(Res.drawable.symbol_send, "Send now", { choose(null) }) })
            RowGroup(
                sendLaterChoices(now)
                    .filter { it.second <= latest }
                    .map { (choice, at) ->
                        { GroupRow(choice.symbol, choice.label, { choose(at) }, value = at.sendLaterLabel(now)) }
                    },
            )
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
            Text(
                "Your mail server sends it, even if your phone is off.",
                Modifier.padding(bottom = SheetTokens.inset),
                color = OtusColors.text3,
            )
        }
    }
    if (pickingDate) {
        SpecificDatePicker(
            now.date.yearMonth,
            now.date..latest.date,
            onDismiss = { pickingDate = false },
            onConfirm = { date ->
                pickingDate = false
                pickedDate = date
            },
            initialDay = now.date.day,
        )
    }
    pickedDate?.let { date ->
        TimeWheelPicker("Choose time", MORNING_HOUR, 0, "Schedule", { pickedDate = null }) { hour, minute ->
            pickedDate = null
            choose(date.atTime(hour, minute).coerceIn(now, latest))
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
