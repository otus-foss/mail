package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.otus.dateformat.currentLocalDate
import kotlinx.datetime.LocalDate

/**
 * The day mail lists count their dates from, or null for the device's date. Screenshot tests pin
 * it so rows read the same any day.
 */
val LocalToday: ProvidableCompositionLocal<LocalDate?> = compositionLocalOf { null }

/** The device's date, or the pinned [LocalToday]. */
@Composable
fun mailToday(): LocalDate = LocalToday.current ?: currentLocalDate()
