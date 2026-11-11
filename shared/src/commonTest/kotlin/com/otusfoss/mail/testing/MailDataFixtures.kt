package com.otusfoss.mail.testing

import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.messages.data.EmailChangeQueue
import com.otusfoss.mail.core.messages.data.JmapEmailWriter
import com.otusfoss.mail.feature.compose.data.JmapComposeMail
import com.otusfoss.mail.feature.compose.data.draftMessageId

/** The email writer the app builds, over [connections] and [cache], with changes kept in [queue]. */
internal fun emailWriter(
    connections: JmapConnections,
    cache: MailCache = InMemoryMailCache(),
    queue: EmailChangeQueue = EmailChangeQueue(connections, cache, store = null),
): JmapEmailWriter = JmapEmailWriter(connections, cache, queue)

/** Saving and sending over [connections], as the app builds them, with one cache for folders and marks. */
internal fun composeMail(
    connections: JmapConnections,
    newMessageId: () -> String = ::draftMessageId,
): JmapComposeMail {
    val cache = InMemoryMailCache()
    val changes = EmailChangeQueue(connections, cache, store = null)
    return JmapComposeMail(connections, Mailboxes(cache), changes, newMessageId)
}
