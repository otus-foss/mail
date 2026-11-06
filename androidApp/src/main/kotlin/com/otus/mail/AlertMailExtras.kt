package com.otus.mail

import android.content.Intent
import androidx.work.Data
import com.otus.mail.feature.notifications.domain.NewMail

// What a button carries about its alert, so a change that fails can post the same alert again.
private const val EXTRA_MAIL = "com.otus.mail.extra.MAIL"

/** This intent with [mail]'s alert in its extras. */
internal fun Intent.putMail(mail: NewMail): Intent =
    putExtra(EXTRA_LOGIN_ID, mail.loginId)
        .putExtra(EXTRA_EMAIL_ID, mail.emailId)
        .putExtra(EXTRA_MAIL, mail.encoded())

/** The alert [putMail] put in this intent; null without it. */
internal fun Intent.mail(): NewMail? = NewMail.decoded(getStringExtra(EXTRA_MAIL))

/** [mail]'s alert as work input, next to what [Data.Builder] already holds. */
internal fun Data.Builder.putMail(mail: NewMail): Data.Builder =
    putString(EXTRA_LOGIN_ID, mail.loginId)
        .putString(EXTRA_EMAIL_ID, mail.emailId)
        .putString(EXTRA_MAIL, mail.encoded())

/** The alert [Data.Builder.putMail] put in this input; null without it. */
internal fun Data.mail(): NewMail? = NewMail.decoded(getString(EXTRA_MAIL))
