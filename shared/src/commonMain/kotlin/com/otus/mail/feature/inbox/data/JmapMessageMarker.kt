package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.login.LoginStore
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.MessageMarker
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Sets or clears one keyword with an `Email/set` patch (RFC 8621, sections 4.1.1 and 4.6). Other
 * keywords stay as they are. A cleared keyword is removed, because keyword values must be true.
 */
class JmapMessageMarker(
    private val connections: JmapConnections,
    private val logins: LoginStore,
) : MessageMarker {
    override suspend fun mark(
        message: MailMessage,
        flag: MessageFlag,
        on: Boolean,
    ): Boolean {
        val patch = buildJsonObject { put("keywords/${flag.keyword}", if (on) JsonPrimitive(true) else JsonNull) }
        return updateEmail(connections, logins, message, patch)
    }
}

internal val MessageFlag.keyword: String
    get() =
        when (this) {
            MessageFlag.Read -> SEEN_KEYWORD
            MessageFlag.Flagged -> FLAGGED_KEYWORD
        }

internal const val SEEN_KEYWORD = "\$seen"
internal const val FLAGGED_KEYWORD = "\$flagged"
