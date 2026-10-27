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
