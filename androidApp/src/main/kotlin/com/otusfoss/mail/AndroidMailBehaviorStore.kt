package com.otusfoss.mail

import android.content.Context
import com.otusfoss.core.storage.SharedPreferenceValues
import com.otusfoss.mail.core.settings.data.KeyValueMailBehaviorStore
import com.otusfoss.mail.core.settings.domain.MailBehaviorStore

class AndroidMailBehaviorStore(
    context: Context,
) : MailBehaviorStore by KeyValueMailBehaviorStore(SharedPreferenceValues(context, "otus_mail_behavior"))
