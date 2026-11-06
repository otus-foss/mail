package com.otus.mail.feature.compose.domain

import com.otus.mail.core.messages.domain.SendIdentity
import com.otus.mail.core.messages.domain.Signature
import com.otus.mail.core.messages.domain.signature

/** Saves signatures on the server's identities (RFC 8621, `Identity/set`). */
fun interface SignatureRepository {
    /** Replaces [identity]'s signature with [signature]. A failure leaves it unchanged. */
    suspend fun save(
        identity: SendIdentity,
        signature: Signature,
    ): Result<Unit>
}
