package com.otus.mail.app

import com.otus.accounts.domain.AccountService
import com.otus.jmap.JmapCapability
import com.otus.jmap.login.LoginLifecycle
import com.otus.mail.feature.compose.domain.MailOutbox
import com.otus.mail.feature.notifications.domain.InstantPush

/** JMAP Mail (RFC 8621), which connecting checks with `Mailbox/get`. */
internal val MailAccountService = AccountService("mail", JmapCapability.MAIL, "Mailbox")

/**
 * What Mail keeps on the device for a login outside its cache: its pushes, its settings in
 * [stores], and the messages [outbox] holds. Removing the login deletes them while its credentials
 * still exist, so the push subscription on the server goes too.
 */
internal class DeviceAccountData(
    private val stores: MailDeviceStores,
    private val outbox: MailOutbox,
    private val push: InstantPush?,
) : LoginLifecycle {
    override suspend fun removing(loginId: String) {
        push?.forget(loginId)
        stores.forgetAccount(loginId)
        outbox.forget(loginId)
    }
}
