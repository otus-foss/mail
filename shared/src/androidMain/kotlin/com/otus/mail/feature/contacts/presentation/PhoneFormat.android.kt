package com.otus.mail.feature.contacts.presentation

import android.telephony.PhoneNumberUtils
import java.util.Locale

// Android's copy of libphonenumber knows every country code and its grouping.
internal actual fun formatPhone(number: String): String =
    PhoneNumberUtils.formatNumber(number.trim(), Locale.getDefault().country) ?: number
