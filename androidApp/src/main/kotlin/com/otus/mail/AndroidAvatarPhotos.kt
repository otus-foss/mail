package com.otus.mail

import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.otus.avatar.AvatarImage
import com.otus.avatar.AvatarPhotoPicker
import com.otus.avatar.ContactAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Picks an avatar photo and crops its center to a square JPEG of at most [SIZE] pixels a side. */
class AndroidAvatarPhotos(
    private val photos: AndroidPhotoPicker,
) : AvatarPhotoPicker {
    override suspend fun pick(): ContactAvatar.Photo? {
        val source = photos.pick() ?: return null
        return withContext(Dispatchers.Default) {
            val side = minOf(source.width, source.height)
            val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
            val scaled = square.scale(minOf(side, SIZE), minOf(side, SIZE))
            ContactAvatar.Photo(AvatarImage(scaled.encoded(Bitmap.CompressFormat.JPEG, QUALITY)), "image/jpeg")
        }
    }

    private companion object {
        const val SIZE = 512
        const val QUALITY = 85
    }
}
