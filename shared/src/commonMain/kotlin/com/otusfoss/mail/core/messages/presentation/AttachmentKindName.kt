package com.otusfoss.mail.core.messages.presentation

import com.otusfoss.core.designsystem.mail.attachments.components.AttachmentKind
import com.otusfoss.mail.core.messages.domain.MailAttachment
import com.otusfoss.mail.core.text.sizeText

/**
 * What kind of file an attachment is, which picks its icon and the word its detail line uses. A
 * kind whose [label] is null is named by its extension instead, as in "ZIP" or "MP3".
 */
enum class AttachmentKindName(
    val label: String?,
) {
    Image("Image"),
    Pdf("PDF"),
    Document("Document"),
    Text("Text"),
    Sheet("Spreadsheet"),
    Data(null),
    Slides("Presentation"),
    Design(null),
    Archive(null),
    DiskImage("Disk image"),
    Audio(null),
    Video(null),
    Calendar("Event"),
    Contact("Contact"),
    Email("Email"),
    Web("Web page"),
    Code(null),
    Key("Key"),
    Signature("Signature"),
    Encrypted("Encrypted"),
    App(null),
    Font("Font"),
    Ebook("Ebook"),
    Location(null),
    Database(null),
    Other(null),
}

/** The kind of this file: the first rule that its media type or its name's extension matches. */
fun MailAttachment.kind(): AttachmentKindName {
    val type = type.lowercase()
    return KIND_RULES.firstOrNull { it.matches(type, extension) }?.kind ?: AttachmentKindName.Other
}

/** The design system's icon for this kind, which shares the kind's name. */
val AttachmentKindName.symbol: AttachmentKind
    get() = AttachmentKind.valueOf(name)

/** Whether this file is an image the app can draw as a picture. Android cannot decode TIFF. */
fun MailAttachment.drawable(): Boolean =
    kind() == AttachmentKindName.Image && extension !in UNDRAWABLE_IMAGES && "tiff" !in type.lowercase()

/**
 * The line under the file's name: its kind and size, as in "PDF · 240 KB" or "ZIP · 1 MB", and
 * "Offline" after them when the user [kept] it on the device.
 */
fun MailAttachment.detail(kept: Boolean = false): String {
    val kind = kind().label ?: extension.uppercase().ifBlank { "File" }
    return if (kept) "$kind · ${sizeText(size)} · Offline" else "$kind · ${sizeText(size)}"
}

/** The short type written on the file's page icon: its extension, as in "PDF", or "FILE" without one. */
fun MailAttachment.typeLabel(): String = extension.uppercase().take(TYPE_LABEL_LENGTH).ifBlank { "FILE" }

/** The attachment strip's line: how many files and their total size, as in "3 attachments · 4.2 MB". */
fun attachmentSummary(files: List<MailAttachment>): String {
    val count = if (files.size == 1) "1 attachment" else "${files.size} attachments"
    return "$count · ${sizeText(files.sumOf { it.size })}"
}

private val UNDRAWABLE_IMAGES = setOf("tif", "tiff")

/** The most letters a page icon's label has room for, as in "DOCX". */
private const val TYPE_LABEL_LENGTH = 4

private val MailAttachment.extension: String
    get() = name.substringAfterLast('.', "").lowercase()

/** A [kind] for media types that contain one of [types], or names that end in one of [extensions]. */
private class KindRule(
    val kind: AttachmentKindName,
    val types: List<String>,
    val extensions: Set<String>,
) {
    fun matches(
        type: String,
        extension: String,
    ): Boolean = types.any(type::contains) || extension in extensions
}

// In order: the first match wins. The encrypted, signed and key kinds come before the formats
// they wrap, design before images for SVG, apps before archives for an APK sent as a ZIP, and
// office and map types before data, since their media types name XML.
private val KIND_RULES =
    listOf(
        KindRule(
            AttachmentKindName.Encrypted,
            listOf("pgp-encrypted", "pkcs7-mime"),
            setOf("gpg", "pgp", "p7m", "age"),
        ),
        KindRule(AttachmentKindName.Signature, listOf("pgp-signature", "pkcs7-signature"), setOf("sig", "p7s")),
        KindRule(
            AttachmentKindName.Key,
            listOf("pgp-keys", "x-pem", "pkcs12", "x-x509"),
            setOf("pem", "crt", "cer", "p12", "pfx", "pub"),
        ),
        KindRule(
            AttachmentKindName.Design,
            listOf("image/svg", "figma", "illustrator", "photoshop", "sketch"),
            setOf("svg", "fig", "ai", "psd", "sketch", "xd", "eps"),
        ),
        KindRule(
            AttachmentKindName.Image,
            listOf("image/"),
            setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp", "tif", "tiff"),
        ),
        KindRule(AttachmentKindName.Pdf, listOf("application/pdf"), setOf("pdf")),
        KindRule(AttachmentKindName.Sheet, listOf("spreadsheet", "excel"), setOf("xls", "xlsx", "ods", "numbers")),
        KindRule(AttachmentKindName.Slides, listOf("presentation", "powerpoint"), setOf("ppt", "pptx", "odp", "key")),
        KindRule(
            AttachmentKindName.Document,
            listOf("wordprocessing", "msword", "opendocument.text", "rtf"),
            setOf("doc", "docx", "odt", "rtf", "pages"),
        ),
        KindRule(AttachmentKindName.Ebook, listOf("epub", "mobipocket"), setOf("epub", "mobi", "azw3")),
        KindRule(AttachmentKindName.Calendar, listOf("text/calendar", "application/ics"), setOf("ics", "ifb")),
        KindRule(AttachmentKindName.Contact, listOf("vcard"), setOf("vcf")),
        KindRule(AttachmentKindName.Email, listOf("message/rfc822", "outlook"), setOf("eml", "msg", "mbox")),
        KindRule(AttachmentKindName.Location, listOf("gpx", "google-earth"), setOf("gpx", "kml", "kmz")),
        KindRule(
            AttachmentKindName.App,
            listOf("android.package", "msdownload", "x-msi", "debian-package", "x-rpm"),
            setOf("apk", "exe", "msi", "appimage", "deb", "rpm", "ipa"),
        ),
        KindRule(
            AttachmentKindName.Archive,
            listOf("zip", "x-7z", "x-rar", "x-tar", "gzip", "x-bzip", "x-xz"),
            setOf("zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz"),
        ),
        KindRule(AttachmentKindName.DiskImage, listOf("diskimage", "iso9660"), setOf("dmg", "iso", "img")),
        KindRule(AttachmentKindName.Font, listOf("font/", "x-font"), setOf("ttf", "otf", "woff", "woff2")),
        KindRule(AttachmentKindName.Audio, listOf("audio/"), setOf("mp3", "m4a", "wav", "ogg", "flac", "aac", "opus")),
        KindRule(AttachmentKindName.Video, listOf("video/"), setOf("mp4", "mov", "mkv", "webm", "avi", "m4v")),
        KindRule(AttachmentKindName.Database, listOf("sqlite", "msaccess"), setOf("db", "sqlite", "mdb")),
        KindRule(AttachmentKindName.Web, listOf("text/html"), setOf("html", "htm")),
        KindRule(
            AttachmentKindName.Data,
            listOf("text/csv", "tab-separated", "json", "xml", "yaml"),
            setOf("csv", "tsv", "json", "xml", "yaml", "yml"),
        ),
        KindRule(AttachmentKindName.Text, listOf("text/plain", "markdown"), setOf("txt", "md", "log")),
        KindRule(
            AttachmentKindName.Code,
            listOf("javascript", "typescript", "x-python", "x-sh", "text/css", "text/x-java", "text/x-c"),
            setOf("js", "ts", "kt", "py", "sh", "css", "sql", "java", "c", "cpp", "h", "rs", "go", "swift", "rb"),
        ),
    )
