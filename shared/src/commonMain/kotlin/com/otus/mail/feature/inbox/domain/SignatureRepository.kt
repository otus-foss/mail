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
    /** Asks the user for an image and shrinks it, or says why it can't be used. */
    suspend fun pick(): SignatureImagePick

    companion object {
        const val MAX_WIDTH = 600
        const val MAX_BYTES = 50_000
    }
}

/** What came of picking a signature image. */
sealed interface SignatureImagePick {
    data class Picked(
        val image: SignatureImage,
    ) : SignatureImagePick

    /** The user closed the picker without choosing an image. */
    data object Cancelled : SignatureImagePick

    /** No encoding fits [SignatureImages.MAX_BYTES]. */
    data object TooLarge : SignatureImagePick

    data object Unreadable : SignatureImagePick
}
