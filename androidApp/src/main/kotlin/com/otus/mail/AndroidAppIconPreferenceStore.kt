package com.otus.mail

import android.content.Context
import com.otus.mail.app.StoredChoice
import com.otus.settings.presentation.AppIconPreferenceStore

class AndroidAppIconPreferenceStore(
    context: Context,
) : AppIconPreferenceStore by StoredChoice(SharedPreferenceValues(context, "otus_mail_app_icon"), "style")
