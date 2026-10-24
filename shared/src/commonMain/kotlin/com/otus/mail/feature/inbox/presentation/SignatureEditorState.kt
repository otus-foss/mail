package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.otus.designsystem.elements.RichText
import com.otus.mail.feature.inbox.domain.SendIdentity
import com.otus.mail.feature.inbox.domain.Signature
import com.otus.mail.feature.inbox.domain.SignatureImage
import com.otus.mail.feature.inbox.domain.SignatureParts
import com.otus.mail.feature.inbox.domain.parseSignature
import com.otus.mail.feature.inbox.domain.signature
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

/**
 * One identity's signature as the user edits it: its formatted [text] and the [images] under it.
 * Edits save [saveDelayMillis] after the last one, and [switchTo] or [close] saves at once.
 */
class SignatureEditorState(
    private val signatures: SignaturesState,
    identity: SendIdentity,
    private val saveDelayMillis: Long = SAVE_DELAY_MILLIS,
) {
    var identity by mutableStateOf(identity)
        private set

    private var parts = parseSignature(identity.htmlSignature)

    val text = RichText(parts.textHtml)

    var images by mutableStateOf(parts.images)
        private set

    /** The signature as last loaded or saved, so loading alone never saves. */
    private var stored = current()

    // Rich text normalizes the loaded text, so compare server acknowledgements with the original value.
    private var baseline = identity.signature
    private var pending: Signature? = null
    private var session = 0L

    /** The image showing its handles, by index. */
    var selected by mutableStateOf<Int?>(null)

    /** Copies every edit of the text or images to the identity, after a pause. Runs until cancelled. */
    suspend fun follow() {
        snapshotFlow { Triple(identity.id, text.html, images) }
            .drop(1)
            .collectLatest { (id, _, _) ->
                if (id != identity.id) return@collectLatest
                delay(saveDelayMillis)
                save()
            }
    }

    /** Saves this signature, then edits [other]'s in its place. */
    fun switchTo(other: SendIdentity) {
        save()
        reload(other)
    }

    /** Replaces the editor after a copy without saving the contents it replaced. */
    internal fun reload(other: SendIdentity) {
        session += 1
        identity = other
        parts = parseSignature(other.htmlSignature)
        text.setHtml(parts.textHtml)
        images = parts.images
        selected = null
        stored = current()
        baseline = other.signature
        pending = null
    }

    fun addImage(image: SignatureImage) {
        images = images + image
        selected = images.lastIndex
    }

    fun resize(
        index: Int,
        width: Int,
    ) {
        images = images.mapIndexed { at, image -> if (at == index) image.copy(width = width) else image }
    }

    fun removeImage(index: Int) {
        images = images.filterIndexed { at, _ -> at != index }
        selected = null
    }

    /** Empties the signature and saves it. */
    fun remove() {
        text.setHtml("")
        images = emptyList()
        selected = null
        save()
    }

    fun close() = save()

    private fun save() {
        val signature = current()
        val acknowledged = signatures.savedSignature(identity)
        val unchanged = signature == stored && (acknowledged == null || acknowledged == baseline)
        if ((unchanged && pending == null) || signature == pending) return
        val edited = identity
        val savingSession = session
        pending = signature
        signatures.save(edited, signature) { saved ->
            if (session == savingSession) {
                if (saved) {
                    stored = signature
                    baseline = signature
                }
                if (pending == signature) pending = null
            }
        }
    }

    private fun current(): Signature = Signature(SignatureParts(text.html, images).html(), text.text)
}

private const val SAVE_DELAY_MILLIS = 1_000L
