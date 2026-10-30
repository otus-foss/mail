package com.otus.mail

import android.content.Context
import android.os.Looper
import android.view.WindowManager
import com.otus.mail.feature.inbox.domain.MailBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScreenSecurityTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun secure(): Boolean {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        return activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    }

    @Test
    fun theScreenStaysCapturableUntilScreenSecurityIsOn() {
        context
            .getSharedPreferences("otus_mail_behavior", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        assertFalse(secure())
        AndroidMailBehaviorStore(context).write(MailBehavior(screenSecurity = true))
        assertTrue(secure())
    }

    @Test
    fun theKeyboardIsIncognitoByDefaultAndBothChoicesSurviveReopeningTheStore() {
        context
            .getSharedPreferences("otus_mail_behavior", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        val defaults = AndroidMailBehaviorStore(context).read()
        assertTrue(defaults.incognitoKeyboard)
        assertFalse(defaults.screenSecurity)
        val saved = MailBehavior(screenSecurity = true, incognitoKeyboard = false)
        AndroidMailBehaviorStore(context).write(saved)
        assertEquals(saved, AndroidMailBehaviorStore(context).read())
    }
}
