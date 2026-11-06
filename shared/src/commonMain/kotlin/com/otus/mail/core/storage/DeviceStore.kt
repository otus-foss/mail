package com.otus.mail.core.storage

import com.otus.storage.StringMapStore

/**
 * One app-private store on the device: its [name], the text form of its [key]s and [value]s, and
 * whether each write [commit]s at once, so what waits for the server survives the app closing right
 * after. Renaming a store, or changing its text form, loses what users saved in it.
 */
abstract class StoreSpec<K, V>(
    val name: String,
    val key: TextCodec<K>,
    val value: TextCodec<V>,
    val commit: Boolean = false,
)

/** The values of [spec] kept in [map]. An entry whose key or value no longer reads counts as missing. */
class DeviceStore<K, V>(
    private val map: StringMapStore,
    private val spec: StoreSpec<K, V>,
) {
    fun read(): Map<K, V> =
        map
            .read()
            .mapNotNull { (key, value) -> decoded(key, value) }
            .toMap()

    /** Replaces every saved value with [values]. */
    fun write(values: Map<K, V>) =
        map.write(values.entries.associate { (key, value) -> spec.key.encode(key) to spec.value.encode(value) })

    fun update(change: (Map<K, V>) -> Map<K, V>) = write(change(read()))

    private fun decoded(
        key: String,
        value: String,
    ): Pair<K, V>? {
        val decodedKey = spec.key.decode(key) ?: return null
        return spec.value.decode(value)?.let { decodedKey to it }
    }
}

/** One value of a store that keeps different kinds of value as text under fixed keys. */
class StoreField<T>(
    val key: String,
    private val codec: TextCodec<T>,
) {
    /** The value in [saved]; null when it is missing or no longer reads. */
    fun of(saved: Map<String, String>): T? = saved[key]?.let(codec.decode)

    /** The entry that saves [value]. */
    fun entry(value: T): Pair<String, String> = key to codec.encode(value)
}
