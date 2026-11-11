package com.otusfoss.mail.core.messages.domain
import com.otusfoss.mail.core.mail.EmailAddressKey

/**
 * An address an account may send from: a JMAP Identity (RFC 8621, section 6). [sender] carries the
 * account in its `accountKey`. Each identity has one signature, as plain text and as HTML.
 */
data class SendIdentity(
    val id: String,
    val sender: MailSender,
    val textSignature: String = "",
    val htmlSignature: String = "",
) {
    val accountId: String get() = sender.accountKey
    val key: SendIdentityKey get() = SendIdentityKey(accountId, id)
}

/** Identity IDs are unique only within their account. */
data class SendIdentityKey(
    val accountId: String,
    val identityId: String,
)

/**
 * The identity for [address], whose own address [addressOf] gives: one with that exact address,
 * else a wildcard such as `*@example.org`.
 */
internal fun <T> List<T>.sendingAs(
    address: String,
    addressOf: (T) -> String,
): T? {
    val wanted = EmailAddressKey(address)
    val domain = wanted.value.substringAfterLast('@', missingDelimiterValue = "")
    return firstOrNull { EmailAddressKey(addressOf(it)) == wanted }
        ?: firstOrNull { domain.isNotEmpty() && EmailAddressKey(addressOf(it)) == EmailAddressKey("*@$domain") }
}
