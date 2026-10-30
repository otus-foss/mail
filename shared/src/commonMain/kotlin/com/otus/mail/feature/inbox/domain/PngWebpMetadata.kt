package com.otus.mail.feature.inbox.domain

/** Copies every chunk of a PNG [image] but text, EXIF, and time chunks, up to its end chunk. */
internal fun pngWithoutMetadata(image: ImageBytes): ByteArray {
    val kept = mutableListOf(image.slice(0, PNG_SIGNATURE_BYTES))
    var at = PNG_SIGNATURE_BYTES
    var type = ""
    while (type != PNG_END) {
        val length = image.u32(at)
        if (length > image.size - at - PNG_CHUNK_OVERHEAD) malformed()
        val end = at + PNG_CHUNK_OVERHEAD + length.toInt()
        type = image.text(at + INT_BYTES, CHUNK_TYPE_BYTES)
        if (type !in PNG_METADATA) kept += image.slice(at, end)
        at = end
    }
    return joined(kept)
}

/**
 * Copies every chunk of a WebP [image], read little-endian, but EXIF and XMP, clears their flags in
 * an extended header, and sizes the file again.
 */
internal fun webpWithoutMetadata(image: ImageBytes): ByteArray {
    val kept = mutableListOf<ByteArray>()
    var at = WEBP_HEADER_BYTES
    while (at < image.size) {
        val size = image.u32(at + CHUNK_TYPE_BYTES)
        if (size > image.size - at - WEBP_CHUNK_HEADER) malformed()
        // Chunks are padded to an even length; the last one may lack its padding byte.
        val end = minOf(at + WEBP_CHUNK_HEADER + size.toInt() + (size % 2).toInt(), image.size)
        val chunk = image.slice(at, end)
        val type = image.text(at, CHUNK_TYPE_BYTES)
        if (type == "VP8X") chunk[WEBP_CHUNK_HEADER] = chunk[WEBP_CHUNK_HEADER].withoutMetadataFlags()
        if (type != "EXIF" && type != "XMP ") kept += chunk
        at = end
    }
    val body = joined(kept)
    return RIFF.encodeToByteArray() + littleEndian(body.size + WEBP.length) + WEBP.encodeToByteArray() + body
}

private fun Byte.withoutMetadataFlags(): Byte = (toInt() and NO_METADATA_FLAGS).toByte()

internal const val PNG_SIGNATURE_BYTES = 8
internal const val RIFF = "RIFF"
internal const val WEBP = "WEBP"

/** Length, type, and checksum around a chunk's data. */
private const val PNG_CHUNK_OVERHEAD = 12
private const val CHUNK_TYPE_BYTES = 4
private const val PNG_END = "IEND"
private val PNG_METADATA = setOf("tEXt", "zTXt", "iTXt", "eXIf", "tIME")
private const val WEBP_HEADER_BYTES = 12
private const val WEBP_CHUNK_HEADER = 8

/** Every VP8X flag but EXIF (0x08) and XMP (0x04). */
private const val NO_METADATA_FLAGS = 0xF3
