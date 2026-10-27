package com.otus.mail

import com.otus.mail.feature.inbox.data.KeyValueMailBehaviorStore
import com.otus.mail.feature.inbox.domain.MailBehaviorStore

class IosMailBehaviorStore : MailBehaviorStore by KeyValueMailBehaviorStore(UserDefaultsValues("otus.mail."))
