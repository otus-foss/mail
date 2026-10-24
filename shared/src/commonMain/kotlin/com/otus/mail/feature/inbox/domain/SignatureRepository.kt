package com.otus.mail.feature.inbox.domain

/** Saves signatures on the server's identities (RFC 8621, `Identity/set`). */
fun interface SignatureRepository {
    /** Replaces [identity]'s signature with [signature]. A failure leaves it unchanged. */
    suspend fun save(
        identity: SendIdentity,
        signature: Signature,
    ): Result<Unit>
}

/**
 * Turns picked image bytes into a signature image: at most [SignatureImages.MAX_WIDTH] pixels wide
 * and at most [SignatureImages.MAX_BYTES] bytes. Null where the platform cannot pick images.
 */
interface SignatureImages {
    /** Asks the user for an image; null when they choose none or it cannot be read. */
    suspend fun pick(): SignatureImage?

    companion object {
        const val MAX_WIDTH = 600
        const val MAX_BYTES = 50_000
    }
}
