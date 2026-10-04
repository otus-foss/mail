package com.otus.mail.core.dateformat

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidDateFormattingTest {
    @Test
    fun deviceFormattingUsesRegionAsWellAsLanguage() {
        val date = LocalDate(2026, 10, 4)
        assertEquals("10/04/2026", DateFormatter(localeIdentifier = "en-US").format(date))
        assertEquals("04/10/2026", DateFormatter(localeIdentifier = "en-GB").format(date))
        assertEquals("04.10.2026", DateFormatter(localeIdentifier = "de-DE").format(date))
    }

    @Test
    fun freshFormatterReadsTheCurrentPhoneLocaleWhileOverridesStayFixed() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("10/04/2026", DateFormatter().formatIsoDate("2026-10-04"))
            Locale.setDefault(Locale.UK)
            assertEquals("04/10/2026", DateFormatter().formatIsoDate("2026-10-04"))
            assertEquals(
                "10.04.2026",
                DateFormatter(DateFormatPreference.MonthDayYearDots).formatIsoDate("2026-10-04"),
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun timeZoneCannotMoveADateToThePreviousDay() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals("01/01/1970", DateFormatter(localeIdentifier = "en-GB").formatIsoDate("1970-01-01"))
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            assertEquals("31/12/2020", DateFormatter(localeIdentifier = "en-GB").formatIsoDate("2020-12-31"))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun localeCalendarDoesNotReplaceTheDomainGregorianYear() {
        assertEquals("04/10/2026", DateFormatter(localeIdentifier = "th-TH").formatIsoDate("2026-10-04"))
    }

    @Test
    fun preferencesRestoreAndCanReturnToFollowingTheDevice() {
        val context = RuntimeEnvironment.getApplication()
        val first = AndroidDateFormatPreferenceStore(context)
        assertEquals(DateFormatPreference.DeviceLocale, first.read())
        first.write(DateFormatPreference.DayMonthYearSlashes)
        val next = AndroidDateFormatPreferenceStore(context)
        assertEquals(DateFormatPreference.DayMonthYearSlashes, next.read())
        next.write(DateFormatPreference.DeviceLocale)
        assertEquals(DateFormatPreference.DeviceLocale, AndroidDateFormatPreferenceStore(context).read())
    }

    @Test
    fun corruptedStoredChoiceRecoversToDeviceFormatting() {
        val context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences("otus_date_format", 0)
            .edit()
            .putString("date_format", "unknown")
            .apply()
        assertEquals(DateFormatPreference.DeviceLocale, AndroidDateFormatPreferenceStore(context).read())
    }
}
