package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PanelTokens
import com.otus.mail.designsystem.theme.SheetTokens
import kotlinx.datetime.LocalDate

/** Fixed navigation and weekdays above continuous, Monday-based week rows. */
@Composable
internal fun ContinuousCalendar(
    state: LazyListState,
    weeks: CalendarWeeks,
    start: String?,
    end: String?,
    onMonth: (LocalDate) -> Unit,
    onDate: (LocalDate, Boolean) -> Unit,
    onRange: (String?, String?) -> Unit,
) {
    val targets = remember(weeks) { CalendarDragTargets(weeks.first..weeks.last) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(SheetTokens.gap)) {
        VisibleMonthHeading(state, weeks, onMonth, onDate)
        CalendarWeekdays()
        LazyColumn(
            Modifier
                .weight(1f)
                .onGloballyPositioned { targets.viewport = it }
                .calendarRangeGesture(state, targets, start, end, onRange)
                .semantics { contentDescription = "Calendar months" },
            state = state,
        ) {
            // Indices identify fixed weeks from January 1970, without formatting dates during key lookup.
            items(count = weeks.count, key = { it }) { index ->
                val firstDay = remember(weeks, index) { weeks.startOfWeek(index) }
                // Allow navigation to the final weeks without adding dates beyond the current year.
                val space = if (index == weeks.count - 1) Modifier.fillParentMaxHeight() else Modifier
                Box(space) {
                    CalendarWeek(firstDay, start, end, onDate, targets)
                }
            }
        }
    }
}

@Composable
private fun VisibleMonthHeading(
    state: LazyListState,
    weeks: CalendarWeeks,
    onMonth: (LocalDate) -> Unit,
    onDate: (LocalDate, Boolean) -> Unit,
) {
    val visibleMonth by remember(state, weeks) { derivedStateOf { weeks.visibleMonth(state.firstVisibleItemIndex) } }
    CalendarHeading(visibleMonth, weeks.last, onMonth, onDate = onDate)
}

@Composable
private fun CalendarWeekdays() {
    Row(Modifier.fillMaxWidth().semantics { contentDescription = "Calendar weekdays" }) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
            Box(Modifier.weight(1f).heightIn(min = PanelTokens.touchTarget), contentAlignment = Alignment.Center) {
                Text(day, color = OtusColors.text2)
            }
        }
    }
}
