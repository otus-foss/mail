package com.otus.mail

import com.otus.mail.feature.inbox.data.KeyValueMailBehaviorStore
import com.otus.mail.feature.inbox.domain.MailBehaviorStore
import com.otus.storage.UserDefaultsValues

class IosMailBehaviorStore : MailBehaviorStore by KeyValueMailBehaviorStore(UserDefaultsValues("otus.mail."))
