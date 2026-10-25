package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.SendIdentity
import com.otus.mail.feature.inbox.domain.SendIdentityKey
import com.otus.mail.feature.inbox.domain.Signature
import com.otus.mail.feature.inbox.domain.SignatureImage
import com.otus.mail.feature.inbox.domain.SignatureImagePick
import com.otus.mail.feature.inbox.domain.SignatureImages
import com.otus.mail.feature.inbox.domain.SignatureRepository
import com.otus.mail.feature.inbox.domain.signature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/**
 * Every identity's signature for Settings › Signatures. Changes show at once and save to the
 * server; a failed save puts the old signature back and shows a [notice], as does an image that
 * can't be used. [images] picks images where the platform can.
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

    fun save(
        identity: SendIdentity,
        signature: Signature,
    ) = saveAll(listOf(identity), signature)

    /** Gives [targets] the signature of [source], replacing theirs. */
    fun copy(
        source: SendIdentity,
        targets: List<SendIdentity>,
    ) = saveAll(targets, source.signature)

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

    private fun saveAll(
        targets: List<SendIdentity>,
        signature: Signature,
    ) {
        val before = identities
        val keys = targets.map { it.key }.toSet()
        identities = identities.map { if (it.key in keys) it.withSignature(signature) else it }
        scope.launch {
            val failed = targets.map { async { repository.save(it, signature) } }.awaitAll().any { it.isFailure }
            notice = if (failed) "Couldn't save the signature. Check your connection and try again." else null
            if (failed) identities = before
        }
    }
}

private fun SendIdentity.withSignature(signature: Signature): SendIdentity =
    copy(htmlSignature = signature.html, textSignature = signature.text)
