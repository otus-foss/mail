package com.otusfoss.mail.core.mail

import com.otusfoss.core.jmap.JmapException

/** [read]'s result, or null when the server refused that call. */
internal inline fun <T> unlessRefused(read: () -> T): T? =
    try {
        read()
    } catch (expected: JmapException.MethodError) {
        null
    }
