package com.otusfoss.mail

import android.content.Context
import com.otusfoss.core.storage.SharedPreferenceValues
import com.otusfoss.core.storage.StoredChoice

// Each choice keeps its own shared preferences file and key. Changing either loses what users saved.

fun Context.themeChoice(): StoredChoice = StoredChoice(SharedPreferenceValues(this, "otus_mail_theme"), "theme")

fun Context.appIconChoice(): StoredChoice = StoredChoice(SharedPreferenceValues(this, "otus_mail_app_icon"), "style")

fun Context.animationChoice(): StoredChoice = StoredChoice(SharedPreferenceValues(this, "otus_animation"), "speed")

fun Context.dateFormatChoice(): StoredChoice =
    StoredChoice(SharedPreferenceValues(this, "otus_date_format"), "date_format")
