package com.otus.mail

import android.content.Context
import com.otus.mail.app.StoredChoice
import com.otus.settings.presentation.ThemePreferenceStore

class AndroidThemePreferenceStore(
    context: Context,
) : ThemePreferenceStore by StoredChoice(SharedPreferenceValues(context, "otus_mail_theme"), "theme")
