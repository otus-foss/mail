package com.otus.jmap.login

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Encodes the login list for platform stores. Unreadable data is reported, never silently dropped. */
internal object LoginCodec {
    private val serializer = ListSerializer(JmapLogin.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    /** Unreadable data is returned with its cause; the store keeps a copy before replacing it. */
    fun decode(text: String?): Decoded =
        if (text == null) {
            Decoded.Logins(emptyList())
        } else {
            try {
                Decoded.Logins(json.decodeFromString(serializer, text))
            } catch (error: SerializationException) {
                Decoded.Unreadable(error)
            } catch (error: IllegalArgumentException) {
                Decoded.Unreadable(error)
            }
        }

    sealed interface Decoded {
        class Logins(
            val logins: List<JmapLogin>,
        ) : Decoded

        class Unreadable(
            val cause: Exception,
        ) : Decoded
    }

    fun encode(logins: List<JmapLogin>): String = json.encodeToString(serializer, logins)

    const val UNREADABLE_SUFFIX: String = ".unreadable"
}
