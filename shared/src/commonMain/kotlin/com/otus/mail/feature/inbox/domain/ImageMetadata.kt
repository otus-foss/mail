package com.otus.mail.feature.inbox.domain

/**
 * [bytes] without the metadata a camera or editor wrote into them: EXIF, with the place and time a
 * photo was taken and the device that took it, XMP, IPTC, comments, and data trailing the image.
 * Pixels and color profiles stay, and so does a JPEG's orientation, so photos keep their way up.
 * JPEG, PNG, and WebP are recognized by their bytes; other files, and images that do not parse,
 * return unchanged.
 */
fun withoutImageMetadata(bytes: ByteArray): ByteArray =
    when {
        bytes.startsWith(JPEG_START) -> jpegWithoutMetadata(bytes)
        bytes.startsWith(PNG_SIGNATURE) -> pngWithoutMetadata(bytes)
        bytes.startsWith(RIFF) && bytes.text(8, 4) == "WEBP" -> webpWithoutMetadata(bytes)
        else -> null
    } ?: bytes

/** Copies the segments that draw the image, up to its end marker, and keeps only the orientation of its EXIF. */
private fun jpegWithoutMetadata(bytes: ByteArray): ByteArray? {
    val pieces = mutableListOf(JPEG_START)
    var orientationAt = 1
    var orientation = NORMAL_ORIENTATION
    var at = 2
    while (at < bytes.size) {
        if (bytes.u8(at) != MARKER) return null
        // A marker may be padded with any number of 0xFF bytes.
        var code = at + 1
        while (code < bytes.size && bytes.u8(code) == MARKER) code++
        if (code >= bytes.size) return null
        val marker = bytes.u8(code)
        if (marker == END_OF_IMAGE) return join(withOrientation(pieces, orientationAt, orientation) + JPEG_END)
        if (marker in RESTARTS || marker == TEMPORARY) {
            pieces += bytes.copyOfRange(code - 1, code + 1)
            at = code + 1
            continue
        }
        if (code + 2 >= bytes.size) return null
        val end = code + 1 + bytes.u16(code + 1)
        if (bytes.u16(code + 1) < 2 || end > bytes.size) return null
        if (keepsSegment(marker, bytes, code + 3)) pieces += bytes.copyOfRange(code - 1, end)
        if (marker == APP0 && pieces.size == 2) orientationAt = 2
        if (marker == APP1) exifOrientation(bytes, code + 3, end)?.let { orientation = it }
        at = end
        if (marker == START_OF_SCAN) {
            at = scanEnd(bytes, end) ?: return null
            pieces += bytes.copyOfRange(end, at)
        }
    }
    return null
}

/**
 * Whether the segment with [marker], whose data starts at [data], draws the image. Color profiles
 * stay; EXIF, XMP, multi-picture indexes, IPTC, vendor segments, and comments go.
 */
private fun keepsSegment(
    marker: Int,
    bytes: ByteArray,
    data: Int,
): Boolean =
    when (marker) {
        APP0, ADOBE -> true
        APP2 -> bytes.text(data, ICC_PROFILE.length) == ICC_PROFILE
        in APP1..APP15, COMMENT -> false
        else -> true
    }

/** Where the entropy-coded data after a scan header starting at [from] ends: at the next marker. */
private fun scanEnd(
    bytes: ByteArray,
    from: Int,
): Int? {
    var at = from
    while (at + 1 < bytes.size) {
        val next = bytes.u8(at + 1)
        // 0xFF00 is a stuffed 0xFF byte, and restart markers belong to the scan.
        if (bytes.u8(at) == MARKER && next != 0 && next !in RESTARTS) return at
        at++
    }
    return null
}

/** The orientation an EXIF segment, from [start] to [end], records in its first image directory. */
private fun exifOrientation(
    bytes: ByteArray,
    start: Int,
    end: Int,
): Int? {
    if (bytes.text(start, EXIF.size) != EXIF.decodeToString()) return null
    val tiff = start + EXIF.size
    val big =
        when (bytes.text(tiff, 2)) {
            "MM" -> true
            "II" -> false
            else -> return null
        }

    fun u16(at: Int) = if (at + 2 > end) null else if (big) bytes.u16(at) else bytes.u8(at) or (bytes.u8(at + 1) shl 8)

    fun u32(at: Int) =
        if (at + 4 > end) {
            null
        } else {
            (0 until 4).fold(0L) { value, i -> value or (bytes.u8(at + i).toLong() shl (8 * if (big) 3 - i else i)) }
        }
    val directory = tiff + (u32(tiff + 4) ?: return null).coerceAtMost(end.toLong()).toInt()
    val entries = u16(directory) ?: return null
    return (0 until entries)
        .map { directory + 2 + it * EXIF_ENTRY }
        .firstOrNull { u16(it) == ORIENTATION_TAG && u16(it + 2) == SHORT_TYPE }
        ?.let { u16(it + 8) }
        ?.takeIf { it in 2..8 }
}

/** [pieces] with an EXIF segment that records only [orientation] at [index], when it is not upright. */
private fun withOrientation(
    pieces: List<ByteArray>,
    index: Int,
    orientation: Int,
): List<ByteArray> {
    if (orientation == NORMAL_ORIENTATION) return pieces
    val tiff =
        byteArrayOf(0x4D, 0x4D, 0, 0x2A, 0, 0, 0, 8, 0, 1, 1, 0x12, 0, 3, 0, 0, 0, 1) +
            byteArrayOf(0, orientation.toByte(), 0, 0, 0, 0, 0, 0)
    val data = EXIF + tiff
    val length = data.size + 2
    val segment = byteArrayOf(MARKER.toByte(), APP1.toByte(), (length shr 8).toByte(), length.toByte()) + data
    return pieces.subList(0, index) + listOf(segment) + pieces.subList(index, pieces.size)
}

/** Copies every chunk but text, EXIF, and time chunks, up to the image's end chunk. */
private fun pngWithoutMetadata(bytes: ByteArray): ByteArray? {
    val pieces = mutableListOf(PNG_SIGNATURE)
    var at = PNG_SIGNATURE.size
    while (at + PNG_CHUNK_OVERHEAD <= bytes.size) {
        val length = bytes.u32(at) ?: return null
        if (length > bytes.size - at - PNG_CHUNK_OVERHEAD) return null
        val end = at + PNG_CHUNK_OVERHEAD + length
        val type = bytes.text(at + 4, 4)
        if (type !in PNG_METADATA) pieces += bytes.copyOfRange(at, end)
        if (type == "IEND") return join(pieces)
        at = end
    }
    return null
}

/** Copies every chunk but EXIF and XMP, clears their flags in an extended header, and sizes the file again. */
private fun webpWithoutMetadata(bytes: ByteArray): ByteArray? {
    val pieces = mutableListOf<ByteArray>()
    var at = WEBP_HEADER
    while (at + 8 <= bytes.size) {
        val size = bytes.u32le(at + 4)
        if (size < 0 || size > bytes.size - at - 8) return null
        val end = minOf(at + 8 + size + size % 2, bytes.size)
        val chunk = bytes.copyOfRange(at, end)
        when (bytes.text(at, 4)) {
            "EXIF", "XMP " -> Unit
            "VP8X" -> pieces += chunk.also { it[8] = (it[8].toInt() and WEBP_METADATA_FLAGS.inv()).toByte() }
            else -> pieces += chunk
        }
        at = end
    }
    if (at != bytes.size) return null
    val body = join(pieces)
    val size = body.size + 4
    val length = byteArrayOf(size.toByte(), (size shr 8).toByte(), (size shr 16).toByte(), (size shr 24).toByte())
    return RIFF + length + "WEBP".encodeToByteArray() + body
}

private fun join(pieces: List<ByteArray>): ByteArray {
    val joined = ByteArray(pieces.sumOf { it.size })
    var at = 0
    pieces.forEach { piece ->
        piece.copyInto(joined, at)
        at += piece.size
    }
    return joined
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun ByteArray.u8(at: Int): Int = this[at].toInt() and BYTE

private fun ByteArray.u16(at: Int): Int = (u8(at) shl 8) or u8(at + 1)

/** A big-endian length, or null past [Int.MAX_VALUE]. */
private fun ByteArray.u32(at: Int): Int? =
    ((u8(at).toLong() shl 24) or (u8(at + 1).toLong() shl 16) or (u16(at + 2).toLong()))
        .takeIf { it <= Int.MAX_VALUE }
        ?.toInt()

/** A little-endian size; negative past [Int.MAX_VALUE]. */
private fun ByteArray.u32le(at: Int): Int = u8(at) or (u8(at + 1) shl 8) or (u8(at + 2) shl 16) or (u8(at + 3) shl 24)

private fun ByteArray.text(
    at: Int,
    length: Int,
): String = if (at + length > size) "" else copyOfRange(at, at + length).decodeToString()

private const val BYTE = 0xFF
private const val MARKER = 0xFF
private const val END_OF_IMAGE = 0xD9
private const val START_OF_SCAN = 0xDA
private const val APP0 = 0xE0
private const val APP1 = 0xE1
private const val APP2 = 0xE2
private const val ADOBE = 0xEE
private const val APP15 = 0xEF
private const val COMMENT = 0xFE
private const val TEMPORARY = 0x01
private val RESTARTS = 0xD0..0xD7
private const val ICC_PROFILE = "ICC_PROFILE\u0000"
private val JPEG_START = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
private val JPEG_END = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
private val EXIF = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0)
private const val EXIF_ENTRY = 12
private const val ORIENTATION_TAG = 0x0112
private const val SHORT_TYPE = 3
private const val NORMAL_ORIENTATION = 1

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

/** Length, type, and checksum around a chunk's data. */
private const val PNG_CHUNK_OVERHEAD = 12
private val PNG_METADATA = setOf("tEXt", "zTXt", "iTXt", "eXIf", "tIME")

private val RIFF = "RIFF".encodeToByteArray()
private const val WEBP_HEADER = 12

/** The EXIF and XMP flags of a VP8X header. */
private const val WEBP_METADATA_FLAGS = 0x0C
