package com.otusfoss.mail.core.messages.data

import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.jmapCallOr
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.core.mail.EMAIL_FOLDERS_PROPERTIES
import com.otusfoss.mail.core.mail.EmailFoldersType
import com.otusfoss.mail.core.mail.mailAccount

/** The ids of every email in [emailId]'s thread on the server now; empty when the server cannot say. */
internal suspend fun JmapConnections.threadEmailIds(
    loginId: String,
    emailId: String,
): List<String> = jmapCallOr(emptyList()) { mailAccount(loginId)?.threadEmailIds(emailId).orEmpty() }

/** The mailboxes [emailId] is in on the server now; empty when it is gone, null when the server cannot say. */
internal suspend fun JmapConnections.emailMailboxes(
    loginId: String,
    emailId: String,
): Set<String>? =
    try {
        jmapCallOr(null) {
            mailAccount(loginId)?.let { account ->
                account.connection
                    .call(
                        GetMethod(
                            EmailFoldersType,
                            account.id,
                            JmapIds.Of(listOf(emailId)),
                            EMAIL_FOLDERS_PROPERTIES,
                        ),
                    ).list
                    .flatMap { it.mailboxIds.filterValues { kept -> kept }.keys }
                    .toSet()
            }
        }
    } catch (expected: LoginStoreException) {
        null
    }
