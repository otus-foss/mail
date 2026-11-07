package com.otus.mail.core.messages.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One lock per login, made the first time a login asks for it. */
internal class LoginLocks {
    private val lock = Mutex()
    private var locks = emptyMap<String, Mutex>()

    suspend fun of(loginId: String): Mutex =
        lock.withLock {
            val login = locks[loginId] ?: Mutex()
            locks = locks + (loginId to login)
            login
        }
}
