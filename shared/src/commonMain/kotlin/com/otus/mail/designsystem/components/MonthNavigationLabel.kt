package com.otus.mail.designsystem.components

/** Display values and ordering supplied by the caller, independent of date-library types. */
data class MonthNavigationLabel(
    val month: String,
    val year: String,
    val chronologicalOrder: Int,
)
