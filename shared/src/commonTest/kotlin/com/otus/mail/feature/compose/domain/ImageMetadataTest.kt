package com.otus.mail.feature.compose.domain

import com.otus.mail.core.messages.domain.signature
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageMetadataTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun segment(
        marker: Int,
        data: ByteArray,
    ) = bytes(0xFF, marker, (data.size + 2) shr 8, (data.size + 2) and 0xFF) + data

    private fun exif(orientation: Int) =
        "Exif".encodeToByteArray() + bytes(0, 0) +
            bytes(0x49, 0x49, 0x2A, 0, 8, 0, 0, 0, 2, 0) +
            bytes(0x12, 0x01, 3, 0, 1, 0, 0, 0, orientation, 0, 0, 0) +
            bytes(0x25, 0x88, 4, 0, 1, 0, 0, 0, 0, 0, 0, 0) +
            bytes(0, 0, 0, 0) + "GPS 47.37N 8.54E".encodeToByteArray()

    private val jfif = segment(0xE0, "JFIF".encodeToByteArray() + bytes(0, 1, 1, 0, 0, 1, 0, 1, 0, 0))
    private val profile = segment(0xE2, "ICC_PROFILE".encodeToByteArray() + bytes(0, 1, 1, 7, 7))
    private val tables = segment(0xDB, bytes(0, 1, 2, 3))
    private val scan = segment(0xDA, bytes(1, 1, 0, 0, 0x3F, 0)) + bytes(0x12, 0xFF, 0x00, 0x34, 0xFF, 0xD0, 0x56)

    private fun jpeg(vararg segments: ByteArray) =
        bytes(0xFF, 0xD8) + segments.fold(ByteArray(0)) { all, it -> all + it } + bytes(0xFF, 0xD9)

    private fun ByteArray.has(text: String) = decodeToString().contains(text)

    @Test
    fun aJpegLosesExifXmpCommentsAndTrailersButKeepsItsPixelsAndProfile() {
        val photo =
            jpeg(
                jfif,
                segment(0xE1, exif(orientation = 1)),
                segment(0xE1, "http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta/>".encodeToByteArray()),
                profile,
                segment(0xED, "Photoshop 3.0".encodeToByteArray()),
                segment(0xFE, "Taken at home".encodeToByteArray()),
                tables,
                scan,
            ) + "SEFT trailer from the camera".encodeToByteArray()
        val clean = assertNotNull(withoutImageMetadata(photo))
        assertContentEquals(jpeg(jfif, profile, tables, scan), clean)
        listOf("Exif", "GPS", "xmpmeta", "Photoshop", "Taken at home", "SEFT").forEach {
            assertFalse(clean.has(it), it)
        }
    }

    @Test
    fun aJpegKeepsOnlyItsOrientationFromExif() {
        val clean = assertNotNull(withoutImageMetadata(jpeg(jfif, segment(0xE1, exif(orientation = 6)), tables, scan)))
        val orientation =
            segment(
                0xE1,
                "Exif".encodeToByteArray() + bytes(0, 0, 0x4D, 0x4D, 0, 0x2A, 0, 0, 0, 8, 0, 1) +
                    bytes(1, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0, 0, 0, 0, 0),
            )
        assertContentEquals(jpeg(jfif, orientation, tables, scan), clean)
        assertFalse(clean.has("GPS"))
    }

    private fun chunk(
        type: String,
        data: String = "",
    ) = bytes(0, 0, 0, data.length) + (type + data).encodeToByteArray() + bytes(1, 2, 3, 4)

    private val signature = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    @Test
    fun aPngLosesItsTextExifAndTimeChunks() {
        val kept = listOf(chunk("IHDR", "size"), chunk("iCCP", "icc"), chunk("IDAT", "pixels"), chunk("IEND"))
        val image =
            signature + kept[0] + kept[1] + chunk("tEXt", "Author") + chunk("eXIf", "GPS") +
                chunk("iTXt", "XMP") + chunk("tIME", "now") + kept[2] + kept[3] + "trailer".encodeToByteArray()
        assertContentEquals(kept.fold(signature) { all, it -> all + it }, withoutImageMetadata(image))
    }

    @Test
    fun aWebpLosesExifAndXmpAndTheirFlags() {
        fun chunk(
            type: String,
            vararg data: Int,
        ): ByteArray {
            val padding = if (data.size % 2 == 1) bytes(0) else bytes()
            return type.encodeToByteArray() + bytes(data.size, 0, 0, 0) + bytes(*data) + padding
        }

        fun webp(vararg chunks: ByteArray): ByteArray {
            val body = chunks.fold(ByteArray(0)) { all, it -> all + it }
            return "RIFF".encodeToByteArray() + bytes(body.size + 4, 0, 0, 0) + "WEBP".encodeToByteArray() + body
        }
        val header = chunk("VP8X", 0x2C, 0, 0, 0, 1, 0, 0, 1, 0, 0)
        val image = webp(header, chunk("VP8 ", 9, 9, 9), chunk("EXIF", 7), chunk("XMP ", 8, 8))
        val clean = withoutImageMetadata(image)
        assertContentEquals(webp(chunk("VP8X", 0x20, 0, 0, 0, 1, 0, 0, 1, 0, 0), chunk("VP8 ", 9, 9, 9)), clean)
    }

    @Test
    fun aJpegCutOffInsideItsScanStillLosesItsMetadata() {
        val photo = jpeg(jfif, segment(0xE1, exif(orientation = 1)), tables, scan)
        val cut = photo.copyOfRange(0, photo.size - 2)
        assertContentEquals(jpeg(jfif, tables, scan), withoutImageMetadata(cut), "The end marker is added")
        val early = photo.copyOfRange(0, photo.size - 5)
        val clean = assertNotNull(withoutImageMetadata(early))
        assertFalse(clean.has("GPS"))
        assertContentEquals(bytes(0x34, 0xFF, 0xD9), clean.copyOfRange(clean.size - 3, clean.size))
    }

    @Test
    fun aPngCutOffInsideAChunkLosesItsMetadataToo() {
        val start = signature + chunk("IHDR", "size") + chunk("tEXt", "GPS 47.37N")
        val pixels = chunk("IDAT", "pixels")
        val cut = start + pixels.copyOfRange(0, 11)
        assertContentEquals(signature + chunk("IHDR", "size") + pixels.copyOfRange(0, 11), withoutImageMetadata(cut))
        val textLast = signature + chunk("IHDR", "size") + pixels + chunk("iTXt", "GPS 47.37N").copyOfRange(0, 12)
        assertContentEquals(signature + chunk("IHDR", "size") + pixels, withoutImageMetadata(textLast))
    }

    @Test
    fun otherFilesStayAsTheyAreAndImagesCutBeforeTheirPixelsCannotBeCleaned() {
        val text = "Exif in a text file".encodeToByteArray()
        assertTrue(withoutImageMetadata(text) === text)
        val cut = jpeg(jfif, segment(0xE1, exif(orientation = 1))).copyOfRange(0, 12)
        assertNull(withoutImageMetadata(cut), "A cut file is never passed off as cleaned")
        assertEquals(0, withoutImageMetadata(ByteArray(0))?.size)
    }
}
