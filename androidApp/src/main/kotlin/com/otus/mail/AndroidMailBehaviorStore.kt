package com.otus.mail

import android.content.Context
import com.otus.mail.feature.inbox.data.KeyValueMailBehaviorStore
import com.otus.mail.feature.inbox.domain.MailBehaviorStore

class AndroidMailBehaviorStore(
    context: Context,
) : MailBehaviorStore by KeyValueMailBehaviorStore(SharedPreferenceValues(context, "otus_mail_behavior"))
