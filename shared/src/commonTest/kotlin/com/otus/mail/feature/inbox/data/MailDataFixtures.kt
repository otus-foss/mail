package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.mailbox.Mailboxes

/** Saving and sending over [connections], as the app builds them. */
internal fun composeMail(
    connections: JmapConnections,
    newMessageId: () -> String = ::draftMessageId,
): JmapComposeMail = JmapComposeMail(connections, Mailboxes(InMemoryMailCache()), newMessageId)
