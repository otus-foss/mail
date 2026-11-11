package com.otusfoss.mail

import com.otusfoss.core.storage.UserDefaultsValues
import com.otusfoss.mail.core.settings.data.KeyValueMailBehaviorStore
import com.otusfoss.mail.core.settings.domain.MailBehaviorStore

class IosMailBehaviorStore : MailBehaviorStore by KeyValueMailBehaviorStore(UserDefaultsValues("otus.mail."))
