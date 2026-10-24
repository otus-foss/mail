package com.otus.mail.feature.inbox.domain

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
