package com.otus.mail.core.files

import java.security.MessageDigest

actual fun storedName(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray()).toHexString()
