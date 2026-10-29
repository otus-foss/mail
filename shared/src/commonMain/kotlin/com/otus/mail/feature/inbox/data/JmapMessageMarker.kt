package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.login.LoginStore
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.MessageMarker

/**
 * Sets or clears one keyword with an `Email/set` patch (RFC 8621, sections 4.1.1 and 4.6). Other
 * keywords stay as they are. A cleared keyword is removed, because keyword values must be true.
 * Each account gets one call per batch of at most its session's `maxObjectsInSet` messages. The
 * emails the server changed change in [cache] too.
 */
class JmapMessageMarker(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    private val cache: MailCache = InMemoryMailCache(),
) : MessageMarker {
    override suspend fun mark(
        messages: List<MailMessage>,
        flag: MessageFlag,
        on: Boolean,
    ): Set<String> = updateEmails(connections, logins, messages, membershipPatch("keywords", flag.keyword, on), cache)
}

internal val MessageFlag.keyword: String
    get() =
        when (this) {
            MessageFlag.Read -> SEEN_KEYWORD
            MessageFlag.Flagged -> FLAGGED_KEYWORD
        }

internal const val SEEN_KEYWORD = "\$seen"
internal const val FLAGGED_KEYWORD = "\$flagged"
internal const val DRAFT_KEYWORD = "\$draft"
