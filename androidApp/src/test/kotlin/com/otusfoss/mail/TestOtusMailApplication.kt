package com.otusfoss.mail

/**
 * The application Robolectric runs in place of [OtusMailApplication], which it finds by the `Test`
 * prefix. It starts as a device that has finished the first run, so tests of the app open on the
 * Inbox; a test of the first run itself saves the flag as false before it launches the activity.
 */
class TestOtusMailApplication : OtusMailApplication() {
    override fun onCreate() {
        val store = AndroidMailBehaviorStore(this)
        store.write(store.read().copy(onboarded = true))
        super.onCreate()
    }
}
