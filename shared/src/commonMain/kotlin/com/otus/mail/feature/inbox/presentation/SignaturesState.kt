package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.core.messages.domain.SendIdentity
import com.otus.mail.core.messages.domain.SendIdentityKey
import com.otus.mail.core.messages.domain.Signature
import com.otus.mail.core.messages.domain.SignatureImage
import com.otus.mail.core.messages.domain.SignatureImagePick
import com.otus.mail.core.messages.domain.SignatureImages
import com.otus.mail.core.messages.domain.signature
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.SignatureRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Every identity's signature for Settings › Mail behavior › Signatures. Changes show at once and
 * save to the server; a failed save puts the old signature back and shows a [notice], as does an
 * image that can't be used. [images] picks images where the platform can.
 */
class SignaturesState(
    private val drafts: DraftRepository,
    private val repository: SignatureRepository,
    val images: SignatureImages?,
    private val scope: CoroutineScope,
    private val accounts: () -> List<ComposeAccount>,
) {
    var identities by mutableStateOf<List<SendIdentity>>(emptyList())
        private set

    var notice by mutableStateOf<String?>(null)
        private set

    /** The accounts, in order, that the identities group under. */
    val accountList: List<ComposeAccount> get() = accounts()

    fun load() {
        scope.launch { identities = accounts().map { async { drafts.identities(it.id) } }.awaitAll().flatten() }
    }

    fun identity(key: SendIdentityKey): SendIdentity? = identities.firstOrNull { it.key == key }

    /** Gives [targets] the signature of [source], replacing theirs. */
    fun copy(
        source: SendIdentity,
        targets: List<SendIdentity>,
    ) {
        targets.forEach { save(it, source.signature) }
    }

    /** Picks an image, or shows a [notice] when the picked one can't be used. Null when there is none. */
    suspend fun pickImage(): SignatureImage? {
        val pick = images?.pick() ?: return null
        notice =
            when (pick) {
                is SignatureImagePick.Picked -> null
                SignatureImagePick.Cancelled -> notice
                SignatureImagePick.TooLarge -> "This image is too large for a signature. Pick a smaller one."
                SignatureImagePick.Unreadable -> "Couldn't read this image. Pick another one."
            }
        return (pick as? SignatureImagePick.Picked)?.image
    }

    internal fun savedSignature(identity: SendIdentity): Signature? = acknowledged[identity.key]

    private val locks = mutableMapOf<SendIdentityKey, Mutex>()
    private val versions = mutableMapOf<SendIdentityKey, Long>()
    private val acknowledged = mutableMapOf<SendIdentityKey, Signature>()
    private var failed = emptySet<SendIdentityKey>()

    fun save(
        identity: SendIdentity,
        signature: Signature,
        onSaved: (Boolean) -> Unit = {},
    ) {
        val key = identity.key
        val version = (versions[key] ?: 0L) + 1
        versions[key] = version
        acknowledged.getOrPut(key) { identity.signature }
        val lock = locks.getOrPut(key) { Mutex() }
        replace(identity, signature)
        scope.launch {
            lock.withLock {
                if (versions[key] != version) {
                    onSaved(false)
                    return@withLock
                }
                val saved = acknowledged[key] == signature || repository.save(identity, signature).isSuccess
                if (saved) acknowledged[key] = signature
                if (versions[key] == version) {
                    failed = if (saved) failed - key else failed + key
                    notice = if (failed.isEmpty()) null else SAVE_FAILURE
                    if (!saved) replace(identity, acknowledged.getValue(key))
                }
                onSaved(saved)
            }
        }
    }

    private fun replace(
        identity: SendIdentity,
        signature: Signature,
    ) {
        identities = identities.map { if (it.key == identity.key) it.withSignature(signature) else it }
    }
}

private fun SendIdentity.withSignature(signature: Signature): SendIdentity =
    copy(htmlSignature = signature.html, textSignature = signature.text)

private const val SAVE_FAILURE = "Couldn't save the signature. Check your connection and try again."
