package com.otus.mail.core.preferences

/**
 * Small values saved under string keys, such as Android shared preferences or iOS user defaults.
 * Each getter returns null for a missing key, so callers pick their own defaults.
 */
interface KeyValueStore {
    fun string(key: String): String?

    fun flag(key: String): Boolean?

    fun int(key: String): Int?

    /** Applies [changes] together. */
    fun edit(changes: KeyValueEditor.() -> Unit)
}

interface KeyValueEditor {
    fun put(
        key: String,
        value: String,
    )

    fun put(
        key: String,
        value: Boolean,
    )

    fun put(
        key: String,
        value: Int,
    )
}

fun KeyValueStore.flag(
    key: String,
    default: Boolean,
): Boolean = flag(key) ?: default

fun KeyValueStore.int(
    key: String,
    default: Int,
): Int = int(key) ?: default

/** The value saved under [key], read with [decode]. */
fun <T> KeyValueStore.decoded(
    key: String,
    decode: (String) -> T,
): T? = string(key)?.let(decode)

/** The value saved under [key], read with [decode], or [default] when nothing is saved. */
fun <T> KeyValueStore.decoded(
    key: String,
    default: T,
    decode: (String) -> T,
): T = decoded(key, decode) ?: default
