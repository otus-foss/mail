package com.otus.mail.designsystem.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object CalendarTokens {
    const val MONTH_COLUMN_WEIGHT = 2f
    const val CONTROLS_HEIGHT_FRACTION = 0.35f
    const val MIN_YEAR = 1970
    const val PICKER_HEIGHT_FRACTION = 0.70f
    val dragScrollEdge = 40.dp
    val dragScrollSpeed = 360.dp
    const val NANOS_PER_SECOND = 1_000_000_000f
    const val MONTH_LABEL_LENGTH = 3
    val monthLabelSize = 12.sp
    val monthLabelLineHeight = 12.sp
    val monthBackground = OtusColors.bgDark
    val alternateMonthBackground = OtusColors.bgRegular
    val monthCornerRadius = 8.dp
    val markerEdgeInset = 2.dp
    val endpointRadius = 4.dp
    val rangeInset = 4.dp
    const val DAYS_PER_WEEK = 7
    const val HALF_TURN = 180f
}
