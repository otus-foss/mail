package com.otus.mail.core.mail

import com.otus.jmap.JmapException
import com.otus.jmap.login.LoginStoreException

/** Runs [call], or returns [fallback] when the server or reading the saved logins fails. */
inline fun <T> mailCallOr(
    fallback: T,
    call: () -> T,
): T =
    try {
        call()
    } catch (expected: JmapException) {
        fallback
    } catch (expected: LoginStoreException) {
        fallback
    }

/** [read]'s result, or null when the server refused that call. */
internal inline fun <T> unlessRefused(read: () -> T): T? =
    try {
        read()
    } catch (expected: JmapException.MethodError) {
        null
    }
