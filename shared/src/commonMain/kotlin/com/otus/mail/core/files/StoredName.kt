package com.otus.mail.core.files

/** A file-name-safe name for [value]: its SHA-256 digest in lowercase hex. */
expect fun storedName(value: String): String
