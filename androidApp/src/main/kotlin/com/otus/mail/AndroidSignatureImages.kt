package com.otus.mail

import com.otus.mail.core.messages.domain.SignatureImagePick
import com.otus.mail.core.messages.domain.SignatureImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Picks signature images with [photos], then shrinks them within the image dimensions and byte
 * limit before the editor accepts them.
 */
class AndroidSignatureImages(
    private val photos: AndroidPhotoPicker,
) : SignatureImages {
    override suspend fun pick(): SignatureImagePick {
        val open = photos.pick() ?: return SignatureImagePick.Cancelled
        return withContext(Dispatchers.IO) { signatureImage(open) }
    }
}
