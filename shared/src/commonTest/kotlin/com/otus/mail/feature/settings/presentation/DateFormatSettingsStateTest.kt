package com.otus.mail.feature.settings.presentation

import com.otus.mail.core.dateformat.DateFormatPreference
import com.otus.mail.core.dateformat.DateFormatPreferenceStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DateFormatSettingsStateTest {
    @Test
    fun selectingAFormatUpdatesStateAndRestoresItForTheNextSession() {
        val store = TestDateFormatStore()
        val state = DateFormatSettingsState(store)
        state.select(DateFormatPreference.DayMonthYearSlashes)
        assertEquals(DateFormatPreference.DayMonthYearSlashes, state.preference)
        assertEquals(state.preference, DateFormatSettingsState(store).preference)
        state.select(DateFormatPreference.DeviceLocale)
        assertEquals(DateFormatPreference.DeviceLocale, DateFormatSettingsState(store).preference)
    }

    @Test
    fun appInstancesKeepTheirOwnPreferences() {
        val mailStore = TestDateFormatStore()
        val calendarStore = TestDateFormatStore()
        DateFormatSettingsState(mailStore).select(DateFormatPreference.MonthDayYearDots)
        assertEquals(DateFormatPreference.DeviceLocale, DateFormatSettingsState(calendarStore).preference)
    }

    @Test
    fun persistenceFailureDoesNotReportAnUnsavedSelectionAsActive() {
        val store =
            object : DateFormatPreferenceStore {
                override fun read() = DateFormatPreference.DeviceLocale

                override fun write(preference: DateFormatPreference) = error("Storage unavailable")
            }
        val state = DateFormatSettingsState(store)
        assertFailsWith<IllegalStateException> { state.select(DateFormatPreference.DayMonthYearDots) }
        assertEquals(DateFormatPreference.DeviceLocale, state.preference)
    }
}

private class TestDateFormatStore : DateFormatPreferenceStore {
    private var value = DateFormatPreference.DeviceLocale

    override fun read(): DateFormatPreference = value

    override fun write(preference: DateFormatPreference) {
        value = preference
    }
}
