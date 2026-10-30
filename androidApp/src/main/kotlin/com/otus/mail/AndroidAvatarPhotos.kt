package com.otus.mail

import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.otus.avatar.AvatarImage
import com.otus.avatar.AvatarPhotoPicker
import com.otus.avatar.ContactAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Picks an avatar photo, decodes it at a bounded size, and crops its center to a square JPEG of at
 * most [SIZE] pixels a side.
 */
class AndroidAvatarPhotos(
    private val photos: AndroidPhotoPicker,
) : AvatarPhotoPicker {
    override suspend fun pick(): ContactAvatar.Photo? {
        val open = photos.pick() ?: return null
        // An unreadable photo is the same as none: the sheet keeps the current avatar.
        return withContext(Dispatchers.IO) { runCatching { sampledBitmap(open, DECODE_SIDE) }.getOrNull() }
            ?.let { withContext(Dispatchers.Default) { it.avatar() } }
    }

    private fun Bitmap.avatar(): ContactAvatar.Photo {
        val side = minOf(width, height)
        return Bitmap
            .createBitmap(this, (width - side) / 2, (height - side) / 2, side, side)
            .scale(minOf(side, SIZE), minOf(side, SIZE))
            .encoded(Bitmap.CompressFormat.JPEG, QUALITY)
            .let { ContactAvatar.Photo(AvatarImage(it), "image/jpeg") }
    }

    private companion object {
        const val SIZE = 512

        /** Room for a center crop of [SIZE] from a photo up to four times as long as it is wide. */
        const val DECODE_SIDE = SIZE * 4
        const val QUALITY = 85
    }
}
