package com.otus.mail.feature.notifications.data

import com.otus.jmap.login.JmapLogin
import com.otus.jmap.push.PushSubscriptions
import com.otus.mail.feature.notifications.domain.MailPush
import com.otus.storage.StringMapStore

/** What pushes need from Mail's services: the saved logins and each login's push subscription. */
interface PushServices {
    /** The saved logins; throws when storage cannot be read, which an empty list would hide. */
    fun notificationLogins(): List<JmapLogin>

    /** The saved logins; none when storage cannot be read. */
    fun logins(): List<JmapLogin>

    /** Keeps each login's subscription pointed at the push endpoint, for the changes in [push]. */
    fun pushSubscriptions(
        states: StringMapStore,
        deviceClientId: String,
        push: MailPush,
        beforeCreate: suspend (String) -> Unit = {},
    ): PushSubscriptions
}
