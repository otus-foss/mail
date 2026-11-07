package com.otus.mail.feature.inbox.presentation

import com.otus.avatar.AvatarOptions
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.feature.inbox.domain.InboxStorage
import com.otus.mail.feature.inbox.domain.MailQuery

internal const val MAX_AUTO_PAGES = 4

/** One load's request number and inputs. [served] once the server's snapshot shows. */
internal class FeedLoad(
    val number: Int,
    val scope: MailScope,
    val query: MailQuery,
    val options: AvatarOptions,
) {
    var served = false
}

internal fun loadNotice(
    reported: String?,
    loading: String?,
): String? = listOfNotNull(reported, loading).distinct().joinToString(" ").ifEmpty { null }

internal val InboxStorage.notice: String
    get() =
        when (this) {
            InboxStorage.Accounts -> "Couldn't load saved accounts. Try again in Settings."
            InboxStorage.Cache -> "Couldn't save cached mail. Refresh to try again."
        }
