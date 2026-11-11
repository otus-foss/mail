package com.otusfoss.mail.core.settings.domain

import com.otusfoss.core.avatar.IconSource
import com.otusfoss.mail.core.messages.domain.RemoteImages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrivacyPresetTest {
    @Test
    fun privateLoadsNothingFromOutsideAndHidesTheScreen() {
        val filled = PrivacyPreset.Private.applyTo(MailBehavior())
        assertEquals(RemoteImages.Never, filled.read.remoteImages)
        assertEquals(PhotoCleaning.Always, filled.write.photoCleaning)
        assertFalse(filled.avatars.websiteIcons)
        assertFalse(filled.avatars.allAccountPhotos)
        assertTrue(filled.privacy.screenSecurity)
        assertTrue(filled.privacy.incognitoKeyboard)
        assertTrue(PrivacyPreset.Private.hidesAlertContent)
    }

    @Test
    fun convenientLoadsImagesFromContactsAndIconsFromDuckDuckGo() {
        val filled = PrivacyPreset.Convenient.applyTo(MailBehavior())
        assertEquals(RemoteImages.Known, filled.read.remoteImages)
        assertEquals(PhotoCleaning.Ask, filled.write.photoCleaning)
        assertTrue(filled.avatars.websiteIcons)
        assertEquals(IconSource.DuckDuckGo, filled.avatars.iconSource)
        assertTrue(filled.avatars.allAccountPhotos)
        assertFalse(filled.privacy.screenSecurity)
        assertFalse(PrivacyPreset.Convenient.hidesAlertContent)
    }

    @Test
    fun aPresetKeepsWhatTheUserTyped() {
        val typed =
            MailBehavior(
                read = ReadBehavior(allowedSenders = listOf("news@example.org")),
                avatars = AvatarBehavior(iconSource = IconSource.Bulwark, bulwarkAddress = "https://icons.example.org"),
            )
        val filled = PrivacyPreset.Private.applyTo(typed)
        assertEquals(typed.read.allowedSenders, filled.read.allowedSenders)
        assertEquals(IconSource.Bulwark, filled.avatars.iconSource)
        assertEquals("https://icons.example.org", filled.avatars.bulwarkAddress)
    }

    @Test
    fun settingsNameThePresetTheyMatchUntilOneChanges() {
        val convenient = PrivacyPreset.Convenient.applyTo(MailBehavior())
        assertEquals(PrivacyPreset.Convenient, convenient.privacyPreset())
        assertEquals(PrivacyPreset.Private, PrivacyPreset.Private.applyTo(convenient).privacyPreset())
        assertNull(convenient.copy(privacy = convenient.privacy.copy(screenSecurity = true)).privacyPreset())
    }

    @Test
    fun aSettingOutsideThePresetDoesNotChangeTheMatch() {
        val filled = PrivacyPreset.Private.applyTo(MailBehavior())
        assertEquals(PrivacyPreset.Private, filled.copy(updateToasts = false).privacyPreset())
    }
}
