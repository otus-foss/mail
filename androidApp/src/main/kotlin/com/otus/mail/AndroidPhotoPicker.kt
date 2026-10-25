package com.otus.mail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** The system photo picker of the activity in front. [pick] returns the chosen photo decoded, or null. */
class AndroidPhotoPicker(
    private val context: Context,
) {
    private var launcher: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var pending: CompletableDeferred<Uri?>? = null

    /** Registers the picker for [activity]; call it from `onCreate`, before the activity starts. */
    fun attach(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(PickVisualMedia()) { pending?.complete(it) }
    }

    suspend fun pick(): Bitmap? {
        val picker = launcher ?: return null
        val result = CompletableDeferred<Uri?>().also { pending = it }
        picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        return result.await()?.let { uri ->
            withContext(
                Dispatchers.IO,
            ) { context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) }
        }
    }
}

fun Bitmap.encoded(
    format: Bitmap.CompressFormat,
    quality: Int,
): ByteArray = ByteArrayOutputStream().also { compress(format, quality, it) }.toByteArray()
