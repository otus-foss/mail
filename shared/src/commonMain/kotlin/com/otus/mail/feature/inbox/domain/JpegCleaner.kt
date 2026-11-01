package com.otus.mail.feature.inbox.domain

/**
 * Copies the segments that draw a JPEG [image], up to its end marker, and keeps only the
 * orientation of its EXIF. Color profiles stay; EXIF, XMP, multi-picture indexes, IPTC, vendor
 * segments, comments, and data after the image go. A file cut off once its first scan began, as by
 * an interrupted write, ends where its data does, with an end marker added; one cut off before
 * that is [MalformedImage].
 */
internal class JpegCleaner(
    private val image: ImageBytes,
) {
    private val kept = mutableListOf<ByteArray>()
    private var orientation = UPRIGHT
    private var orientationAt = 1
    private var scanStarted = false

    fun cleaned(): ByteArray {
        kept += image.slice(0, MARKER_BYTES)
        var at: Int? = MARKER_BYTES
        while (at != null) at = nextOrEnd(at)
        return joined(withOrientation())
    }

    /**
     * [next], or null at a cut once a scan began: what was kept so far holds no metadata, so the
     * image ends there.
     */
    private fun nextOrEnd(at: Int): Int? =
        try {
            next(at)
        } catch (cut: MalformedImage) {
            if (!scanStarted) throw cut
            kept += byteArrayOf(MARKER.toByte(), END_OF_IMAGE.toByte())
            null
        }

    /** Copies the marker at [at] and what belongs to it; where the next one starts, or null after the end. */
    private fun next(at: Int): Int? {
        if (image.u8(at) != MARKER) malformed()
        // A marker may be padded with any number of 0xFF bytes.
        var code = at + 1
        while (image.u8(code) == MARKER) code++
        val marker = image.u8(code)
        return when (marker) {
            END_OF_IMAGE -> keepMarker(code, last = true)
            in FIRST_RESTART..LAST_RESTART, TEMPORARY -> keepMarker(code, last = false)
            else -> segment(code, marker)
        }
    }

    private fun keepMarker(
        code: Int,
        last: Boolean,
    ): Int? {
        kept += image.slice(code - 1, code + 1)
        return if (last) null else code + 1
    }

    /** Copies the segment whose [marker] is at [code] when it draws the image; where the next one starts. */
    private fun segment(
        code: Int,
        marker: Int,
    ): Int {
        val length = image.u16(code + 1)
        if (length < SHORT_BYTES) malformed()
        val data = code + 1 + SHORT_BYTES
        val end = code + 1 + length
        val segment = image.slice(code - 1, end)
        if (keeps(marker, data)) kept += segment
        if (marker == APP0 && kept.size == 2) orientationAt = 2
        if (marker == APP1) {
            val decoded = exifOrientation(image, data, end)
            if (decoded != null) orientation = decoded
        }
        return if (marker == START_OF_SCAN) scanned(end) else end
    }

    /** Whether the segment with [marker], whose data starts at [data], draws the image. */
    private fun keeps(
        marker: Int,
        data: Int,
    ): Boolean =
        when (marker) {
            APP0, ADOBE -> true
            APP2 -> image.text(data, ICC_PROFILE.length) == ICC_PROFILE
            in APP1..APP15, COMMENT -> false
            else -> true
        }

    /**
     * Copies the entropy-coded data after a scan header ending at [from]; where the next marker
     * starts, or the end of the file when it was cut off inside the scan.
     */
    private fun scanned(from: Int): Int {
        scanStarted = true
        var at = from
        while (at < image.size && !endsScan(at)) at++
        kept += image.slice(from, at)
        return at
    }

    /**
     * Whether a marker that ends the scan starts at [at]. 0xFF00 is a stuffed 0xFF byte, and
     * restart markers belong to the scan.
     */
    private fun endsScan(at: Int): Boolean {
        if (image.u8(at) != MARKER || at + 1 >= image.size) return false
        val code = image.u8(at + 1)
        return code != 0 && code !in FIRST_RESTART..LAST_RESTART
    }

    /** The kept segments with an EXIF segment that records only the orientation, when it is not upright. */
    private fun withOrientation(): List<ByteArray> {
        if (orientation == UPRIGHT) return kept
        val tiff = hexBytes(ORIENTATION_START) + byteArrayOf(0, orientation.toByte()) + hexBytes(ORIENTATION_END)
        val data = EXIF_HEADER.encodeToByteArray() + tiff
        val length = data.size + SHORT_BYTES
        val segment = byteArrayOf(MARKER.toByte(), APP1.toByte()) + segmentLength(length) + data
        return kept.subList(0, orientationAt) + listOf(segment) + kept.subList(orientationAt, kept.size)
    }
}

/** [length] as the two big-endian bytes a JPEG segment starts with. */
private fun segmentLength(length: Int) = littleEndian(length).copyOfRange(0, SHORT_BYTES).reversedArray()

/** The orientation an EXIF segment of [image], from [start] to [end], records in its first image directory. */
private fun exifOrientation(
    image: ImageBytes,
    start: Int,
    end: Int,
): Int? {
    val tiff = start + EXIF_HEADER.length
    val order = image.text(tiff, SHORT_BYTES)
    val readable =
        image.text(start, EXIF_HEADER.length) == EXIF_HEADER && (order == BIG_ENDIAN || order == LITTLE_ENDIAN)
    return if (readable) {
        try {
            orientationIn(ImageBytes(image.bytes, end, bigEndian = order == BIG_ENDIAN), tiff)
        } catch (expected: MalformedImage) {
            null
        }
    } else {
        null
    }
}

private fun orientationIn(
    exif: ImageBytes,
    tiff: Int,
): Int? {
    val directory = tiff + exif.u32(tiff + DIRECTORY_OFFSET_AT).coerceAtMost(exif.size.toLong()).toInt()
    return (0 until exif.u16(directory))
        .map { directory + SHORT_BYTES + it * EXIF_ENTRY_BYTES }
        .firstOrNull { exif.u16(it) == ORIENTATION_TAG && exif.u16(it + SHORT_BYTES) == SHORT_TYPE }
        ?.let { exif.u16(it + EXIF_VALUE_AT) }
        ?.takeIf { it in ROTATED..LAST_ORIENTATION }
}

private const val MARKER_BYTES = 2
private const val MARKER = 0xFF
private const val END_OF_IMAGE = 0xD9
private const val START_OF_SCAN = 0xDA
private const val FIRST_RESTART = 0xD0
private const val LAST_RESTART = 0xD7
private const val TEMPORARY = 0x01
private const val APP0 = 0xE0
private const val APP1 = 0xE1
private const val APP2 = 0xE2
private const val ADOBE = 0xEE
private const val APP15 = 0xEF
private const val COMMENT = 0xFE
private const val ICC_PROFILE = "ICC_PROFILE\u0000"
private const val EXIF_HEADER = "Exif\u0000\u0000"
private const val BIG_ENDIAN = "MM"
private const val LITTLE_ENDIAN = "II"
private const val DIRECTORY_OFFSET_AT = 4
private const val EXIF_ENTRY_BYTES = 12
private const val EXIF_VALUE_AT = 8
private const val ORIENTATION_TAG = 0x0112
private const val SHORT_TYPE = 3
private const val UPRIGHT = 1
private const val ROTATED = 2
private const val LAST_ORIENTATION = 8

/** A big-endian TIFF header and one directory entry, an orientation of type short, up to its value. */
private const val ORIENTATION_START = "4d4d002a0000000800010112000300000001"

/** The entry's padding and the end of the directory. */
private const val ORIENTATION_END = "000000000000"
