package com.otus.mail

import com.otus.mail.core.settings.data.KeyValueMailBehaviorStore
import com.otus.mail.core.settings.domain.MailBehaviorStore
import com.otus.storage.UserDefaultsValues

class IosMailBehaviorStore : MailBehaviorStore by KeyValueMailBehaviorStore(UserDefaultsValues("otus.mail."))
