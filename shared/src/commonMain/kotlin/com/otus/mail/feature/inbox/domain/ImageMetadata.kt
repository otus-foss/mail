package com.otus.mail.feature.inbox.domain

/**
 * [bytes] without the metadata a camera or editor wrote into them: EXIF, with the place and time a
 * photo was taken and the device that took it, XMP, IPTC, comments, and data trailing the image.
 * Pixels and color profiles stay, and so does a JPEG's orientation, so photos keep their way up.
 * JPEG, PNG, and WebP are recognized by their bytes; other files, and images that do not parse,
 * return unchanged.
 */
fun withoutImageMetadata(bytes: ByteArray): ByteArray =
    try {
        val image = ImageBytes(bytes)
        when {
            image.isJpeg() -> JpegCleaner(image).cleaned()
            image.isPng() -> pngWithoutMetadata(image)
            image.isWebp() -> webpWithoutMetadata(ImageBytes(bytes, bigEndian = false))
            else -> bytes
        }
    } catch (expected: MalformedImage) {
        bytes
    }

private fun ImageBytes.isJpeg() = size >= JPEG_SIGNATURE_BYTES && u8(0) == JPEG_MARKER && u8(1) == JPEG_START

private fun ImageBytes.isPng() =
    size >= PNG_SIGNATURE_BYTES && u8(0) == PNG_FIRST_BYTE && text(1, PNG_NAME.length) == PNG_NAME

private fun ImageBytes.isWebp() = text(0, RIFF.length) == RIFF && text(WEBP_NAME_AT, WEBP.length) == WEBP

private const val JPEG_SIGNATURE_BYTES = 2
private const val JPEG_MARKER = 0xFF
private const val JPEG_START = 0xD8
private const val PNG_FIRST_BYTE = 0x89

/** What follows the first byte of the PNG signature. */
private const val PNG_NAME = "PNG\r\n\u001A\n"
private const val WEBP_NAME_AT = 8
