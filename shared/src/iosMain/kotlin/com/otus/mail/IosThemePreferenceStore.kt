package com.otus.mail

import com.otus.mail.app.StoredChoice
import com.otus.settings.presentation.ThemePreferenceStore

class IosThemePreferenceStore : ThemePreferenceStore by StoredChoice(UserDefaultsValues("otus.mail."), "theme")
