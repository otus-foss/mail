package com.otus.mail

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import com.otus.mail.feature.inbox.domain.SignatureImagePick
import com.otus.mail.feature.inbox.domain.SignatureImages
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Picks signature images with the system photo picker of the activity in front, then shrinks them
 * within the image dimensions and byte limit before the editor accepts them.
 */
class AndroidSignatureImages(
    private val context: Context,
) : SignatureImages {
    private var launcher: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var pending: CompletableDeferred<Uri?>? = null

    /** Registers the picker for [activity]; call it from `onCreate`, before the activity starts. */
    fun attach(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(PickVisualMedia()) { pending?.complete(it) }
    }

    override suspend fun pick(): SignatureImagePick {
        val picker = launcher ?: return SignatureImagePick.Cancelled
        val result = CompletableDeferred<Uri?>().also { pending = it }
        picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        return result.await()?.let { uri -> withContext(Dispatchers.IO) { shrink(uri) } }
            ?: SignatureImagePick.Cancelled
    }

    private fun shrink(uri: Uri): SignatureImagePick = signatureImage { context.contentResolver.openInputStream(uri) }
}
