package com.otus.mail.core.mail

import com.otus.jmap.JmapJson
import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.CacheWrite
import com.otus.mail.core.cache.MailCache
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/**
 * The cached objects of [kind] with [ids], decoded with [serializer]. An object saved by an older
 * release that no longer decodes counts as missing, so the next load fetches it again.
 */
internal suspend fun <T> MailCache.objects(
    loginId: String,
    kind: CacheKind,
    serializer: KSerializer<T>,
    ids: Collection<String>,
): Map<String, T> = read(loginId, kind, ids).decoded(serializer)

/** Every cached object of [kind] of [loginId], decoded like [objects]. */
internal suspend fun <T> MailCache.allObjects(
    loginId: String,
    kind: CacheKind,
    serializer: KSerializer<T>,
): Map<String, T> = readAll(loginId, kind).decoded(serializer)

internal fun <T> CacheWrite.putObject(
    kind: CacheKind,
    id: String,
    serializer: KSerializer<T>,
    value: T,
    receivedMillis: Long? = null,
) {
    put(kind, id, JmapJson.encodeToString(serializer, value), receivedMillis)
}

/** The login's cached mailboxes, as the last full `Mailbox/get` saved them. */
internal suspend fun MailCache.mailboxes(loginId: String): List<MailboxObject> =
    allObjects(loginId, CacheKind.Mailbox, MailboxObject.serializer()).values.toList()

/**
 * Replaces the login's cached mailboxes with [mailboxes], which must be fetched with
 * [MAILBOX_PROPERTIES]: the inbox and the folders share them, so neither may save fewer properties.
 */
internal fun CacheWrite.replaceMailboxes(mailboxes: List<MailboxObject>) {
    clear(CacheKind.Mailbox)
    mailboxes.forEach { putObject(CacheKind.Mailbox, it.id, MailboxObject.serializer(), it) }
}

internal fun <T> Map<String, String>.decoded(serializer: KSerializer<T>): Map<String, T> =
    mapNotNull { (id, json) ->
        try {
            id to JmapJson.decodeFromString(serializer, json)
        } catch (expected: SerializationException) {
            null
        } catch (expected: IllegalArgumentException) {
            null
        }
    }.toMap()
