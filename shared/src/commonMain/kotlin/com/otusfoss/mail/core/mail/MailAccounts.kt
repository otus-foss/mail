package com.otusfoss.mail.core.mail

import com.otusfoss.core.jmap.JmapCapability
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapLoginAccount
import com.otusfoss.core.jmap.login.JmapLogin

/** One login's connection and the JMAP account that holds its mail. */
internal typealias MailAccount = JmapLoginAccount

/** [login]'s mail account. Throws [com.otusfoss.core.jmap.JmapException.Unsupported] when it has none. */
internal suspend fun JmapConnections.mailAccount(login: JmapLogin): MailAccount = account(login, JmapCapability.MAIL)

/** The mail account of the saved login [loginId], or null once the login was removed. */
internal suspend fun JmapConnections.mailAccount(loginId: String): MailAccount? = account(loginId, JmapCapability.MAIL)
