package com.otus.mail.feature.compose.domain

/**
 * Reads [bytes] up to [end] as an image file: bytes, numbers of [bigEndian] order, and text. A read
 * past [end] throws [MalformedImage], so a parser needs no bounds checks of its own.
 */
internal class ImageBytes(
    val bytes: ByteArray,
    private val end: Int = bytes.size,
    private val bigEndian: Boolean = true,
) {
    val size: Int get() = end

    fun u8(at: Int): Int {
        if (at !in 0 until end) malformed()
        return bytes[at].toInt() and BYTE_MASK
    }

    fun u16(at: Int): Int = number(at, SHORT_BYTES).toInt()

    fun u32(at: Int): Long = number(at, INT_BYTES)

    /** [length] bytes at [at] as text, or empty when they run past the end. */
    fun text(
        at: Int,
        length: Int,
    ): String = if (at < 0 || at + length > end) "" else bytes.copyOfRange(at, at + length).decodeToString()

    fun slice(
        from: Int,
        to: Int,
    ): ByteArray {
        if (from !in 0..to || to > end) malformed()
        return bytes.copyOfRange(from, to)
    }

    private fun number(
        at: Int,
        length: Int,
    ): Long =
        (0 until length).fold(0L) { value, index ->
            val place = if (bigEndian) length - 1 - index else index
            value or (u8(at + index).toLong() shl (BYTE_BITS * place))
        }
}

/** An image whose bytes do not follow its format; the caller keeps the file as it is. */
internal class MalformedImage : Exception("The image does not follow its format")

internal fun malformed(): Nothing = throw MalformedImage()

internal fun joined(pieces: List<ByteArray>): ByteArray {
    val bytes = ByteArray(pieces.sumOf { it.size })
    var at = 0
    pieces.forEach { piece ->
        piece.copyInto(bytes, at)
        at += piece.size
    }
    return bytes
}

internal fun littleEndian(value: Int): ByteArray = ByteArray(INT_BYTES) { (value shr (BYTE_BITS * it)).toByte() }

/** The bytes a string of hex digit pairs, such as `4d4d`, spells. */
internal fun hexBytes(hex: String): ByteArray =
    ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(HEX).toByte() }

internal const val SHORT_BYTES = 2
internal const val INT_BYTES = 4
private const val BYTE_MASK = 0xFF
private const val BYTE_BITS = 8
private const val HEX = 16
