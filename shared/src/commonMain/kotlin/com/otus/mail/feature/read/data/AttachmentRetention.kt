package com.otus.mail.feature.read.data

import com.otus.accounts.domain.AccountStorageException
import com.otus.jmap.login.LoginLifecycle
import com.otus.mail.core.files.DownloadFolder
import com.otus.mail.core.files.DownloadFolderException
import com.otus.mail.core.files.StoredEntry
import com.otus.mail.core.files.StoredKey
import com.otus.storage.storedName

/**
 * When downloaded attachments in [folder] leave the device. Files the user keeps stay until they
 * remove them or the account. The others are cache: the least recently used go once together they
 * pass [budgetBytes], and [trim] drops the ones unused for a while. See docs/storage.md.
 */
class AttachmentRetention(
    private val folder: DownloadFolder,
    private val budgetBytes: Long = UNKEPT_BUDGET_BYTES,
) : LoginLifecycle {
    /**
     * Deletes partial files left by an earlier process, unkept files last used before
     * [unusedBeforeMillis], and then the least recently used unkept files over the budget. Call
     * it once at start. False when the device failed.
     */
    suspend fun trim(unusedBeforeMillis: Long): Boolean =
        try {
            folder.dropUnfinished()
            folder
                .entries()
                .filter { !it.kept && it.usedAtMillis < unusedBeforeMillis }
                .forEach { folder.delete(it.key) }
            fitBudget(newest = null)
            true
        } catch (expected: DownloadFolderException) {
            false
        }

    /**
     * Deletes unkept files, least recently used first, until the rest fit in the budget. The
     * [newest] file, or else the most recently used, always stays, even when it alone is larger.
     * Throws [DownloadFolderException] when the device fails.
     */
    suspend fun fitBudget(newest: StoredKey?) {
        val unkept =
            folder
                .entries()
                .filterNot { it.kept }
                .sortedWith(compareByDescending<StoredEntry> { it.key == newest }.thenByDescending { it.usedAtMillis })
        val totals = unkept.runningFold(0L) { total, entry -> total + entry.bytes }.drop(1)
        unkept
            .zip(totals)
            .drop(1)
            .filter { (_, total) -> total > budgetBytes }
            .forEach { (entry, _) -> folder.delete(entry.key) }
    }

    /**
     * Deletes every file of [loginId], kept or not, as when its account is removed. Throws
     * [DownloadFolderException] when the device fails.
     */
    suspend fun forget(loginId: String) {
        folder.deleteGroup(storedName(loginId))
    }

    override suspend fun removing(loginId: String) {
        try {
            forget(loginId)
        } catch (error: DownloadFolderException) {
            throw AccountStorageException(error)
        }
    }

    private companion object {
        /** How much room unkept files take before the least recently used go. */
        const val UNKEPT_BUDGET_BYTES = 256L * 1024 * 1024
    }
}
