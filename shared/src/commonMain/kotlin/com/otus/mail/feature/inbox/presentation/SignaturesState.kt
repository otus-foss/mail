package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.SendIdentity
import com.otus.mail.feature.inbox.domain.Signature
import com.otus.mail.feature.inbox.domain.SignatureImage
import com.otus.mail.feature.inbox.domain.SignatureImages
import com.otus.mail.feature.inbox.domain.SignatureRepository
import com.otus.mail.feature.inbox.domain.signature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Every identity's signature for Settings › Signatures. Changes show at once and save to the
 * server; a failed save puts the old signature back and shows a [notice]. [images] picks images
 * where the platform can.
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

    fun identity(id: String): SendIdentity? = identities.firstOrNull { it.id == id }

    fun save(
        identity: SendIdentity,
        signature: Signature,
        onSaved: (Boolean) -> Unit = {},
    ) = saveOne(identity, signature, onSaved)

    /** Gives [targets] the signature of [source], replacing theirs. */
    fun copy(
        source: SendIdentity,
        targets: List<SendIdentity>,
    ) = saveAll(targets, source.signature)

    suspend fun pickImage(): SignatureImage? = images?.pick()

    internal fun savedSignature(identity: SendIdentity): Signature? = acknowledged[identity.accountId to identity.id]

    private fun saveAll(
        targets: List<SendIdentity>,
        signature: Signature,
    ) {
        targets.forEach { saveOne(it, signature) {} }
    }

    private val locks = mutableMapOf<Pair<String, String>, Mutex>()
    private val versions = mutableMapOf<Pair<String, String>, Long>()
    private val acknowledged = mutableMapOf<Pair<String, String>, Signature>()
    private var failed = emptySet<Pair<String, String>>()

    private fun saveOne(
        identity: SendIdentity,
        signature: Signature,
        onSaved: (Boolean) -> Unit,
    ) {
        val key = identity.accountId to identity.id
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
        identities =
            identities.map {
                if (it.accountId == identity.accountId && it.id == identity.id) it.withSignature(signature) else it
            }
    }
}

private fun SendIdentity.withSignature(signature: Signature): SendIdentity =
    copy(htmlSignature = signature.html, textSignature = signature.text)

private const val SAVE_FAILURE = "Couldn't save the signature. Check your connection and try again."
