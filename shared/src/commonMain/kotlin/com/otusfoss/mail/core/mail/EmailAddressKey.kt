package com.otusfoss.mail.core.mail

import kotlin.jvm.JvmInline

/**
 * An email address as Mail compares it: trimmed and in lower case, so `Ada@Example.org ` and
 * `ada@example.org` are one person. Show and send the address as it was typed, never this key.
 */
@JvmInline
value class EmailAddressKey private constructor(
    val value: String,
) {
    companion object {
        operator fun invoke(address: String): EmailAddressKey = EmailAddressKey(address.trim().lowercase())
    }
}
