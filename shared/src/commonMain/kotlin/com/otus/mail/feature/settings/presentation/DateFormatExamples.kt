package com.otus.mail.feature.settings.presentation

import com.otus.mail.core.dateformat.DateFormatPreference
import com.otus.mail.core.dateformat.DateFormatter
import kotlinx.datetime.LocalDate

/** "Pattern · example", for example "DD.MM.YYYY · 04.10.2026". */
fun dateFormatSummary(
    preference: DateFormatPreference,
    localeIdentifier: String,
    today: LocalDate,
): String = "${preference.patternLabel} · ${dateFormatExample(preference, localeIdentifier, today)}"

fun dateFormatExample(
    preference: DateFormatPreference,
    localeIdentifier: String,
    today: LocalDate,
): String = DateFormatter(preference, localeIdentifier).format(today)
