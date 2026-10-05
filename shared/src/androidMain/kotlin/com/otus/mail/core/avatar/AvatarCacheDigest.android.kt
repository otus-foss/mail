package com.otus.mail.core.avatar

import java.security.MessageDigest

internal actual fun avatarCacheDigest(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray()).toHexString()
