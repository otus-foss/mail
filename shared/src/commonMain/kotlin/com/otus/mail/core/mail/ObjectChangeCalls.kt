package com.otus.mail.core.mail

import com.otus.jmap.ChangesMethod
import com.otus.jmap.ChangesResult
import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.JmapRequestBuilder
import com.otus.jmap.JmapResponse
import com.otus.jmap.JmapType

/** [read]'s result, or null when the server refused that call. */
internal inline fun <T> unlessRefused(read: () -> T): T? =
    try {
        read()
    } catch (expected: JmapException.MethodError) {
        null
    }

/**
 * The objects a `/changes` call named as created or updated, fetched again, the ids it
 * destroyed, and the new state.
 */
internal class ObjectPatch<T>(
    val fetched: List<T>,
    val destroyed: Set<String>,
    val state: String,
)

/**
 * `Foo/changes` since a saved state (RFC 8620, section 5.2) and the `Foo/get` calls of what it
 * created and updated, by result reference, inside a larger request.
 */
internal class ObjectChangeCalls<T>(
    private val changes: JmapCall<ChangesResult>,
    private val created: JmapCall<GetResult<T>>,
    private val updated: JmapCall<GetResult<T>>,
) {
    /**
     * The patch the response holds; null when the server refused the changes, such as with
     * `cannotCalculateChanges`, or has more than one call returns, so the objects are read anew.
     */
    fun read(response: JmapResponse): ObjectPatch<T>? {
        val found = unlessRefused { response[changes] }?.takeUnless { it.hasMoreChanges } ?: return null
        return ObjectPatch(response[created].list + response[updated].list, found.destroyed.toSet(), found.newState)
    }
}

/** The calls for [type]'s changes in [accountId] since [state], fetching [properties] of each changed object. */
internal fun <T> JmapRequestBuilder.objectChangeCalls(
    type: JmapType<T>,
    accountId: String,
    state: String,
    maxChanges: Int,
    properties: List<String>? = null,
): ObjectChangeCalls<T> {
    val changes = call(ChangesMethod(type, accountId, state, maxChanges))
    return ObjectChangeCalls(
        changes,
        call(GetMethod(type, accountId, JmapIds.Result(changes.resultOf("/created")), properties)),
        call(GetMethod(type, accountId, JmapIds.Result(changes.resultOf("/updated")), properties)),
    )
}
