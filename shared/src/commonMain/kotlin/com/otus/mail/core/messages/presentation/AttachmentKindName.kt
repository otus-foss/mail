package com.otus.mail.core.messages.presentation

import com.otus.mail.core.messages.domain.MailAttachment
import com.otus.mail.core.text.sizeText

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
    Sheet("Spreadsheet"),
    Slides("Presentation"),
    Archive(null),
    Audio(null),
    Video(null),
    Calendar("Event"),
    Contact("Contact"),
    Code(null),
    Other(null),
}

/** The kind of this file: the first rule that its media type or its name's extension matches. */
fun MailAttachment.kind(): AttachmentKindName {
    val type = type.lowercase()
    return KIND_RULES.firstOrNull { it.matches(type, extension) }?.kind ?: AttachmentKindName.Other
}

/**
 * The line under the file's name: its kind and size, as in "PDF · 240 KB" or "ZIP · 1 MB", and
 * "Offline" after them when the user [kept] it on the device.
 */
fun MailAttachment.detail(kept: Boolean = false): String {
    val kind = kind().label ?: extension.uppercase().ifBlank { "File" }
    return if (kept) "$kind · ${sizeText(size)} · Offline" else "$kind · ${sizeText(size)}"
}

/** The attachment strip's line: how many files and their total size, as in "3 attachments · 4.2 MB". */
fun attachmentSummary(files: List<MailAttachment>): String {
    val count = if (files.size == 1) "1 attachment" else "${files.size} attachments"
    return "$count · ${sizeText(files.sumOf { it.size })}"
}

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

// In order: office types come before code, since their media types name XML.
private val KIND_RULES =
    listOf(
        KindRule(
            AttachmentKindName.Image,
            listOf("image/"),
            setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp", "svg"),
        ),
        KindRule(AttachmentKindName.Pdf, listOf("application/pdf"), setOf("pdf")),
        KindRule(
            AttachmentKindName.Sheet,
            listOf("spreadsheet", "excel", "text/csv"),
            setOf("xls", "xlsx", "ods", "csv", "numbers"),
        ),
        KindRule(AttachmentKindName.Slides, listOf("presentation", "powerpoint"), setOf("ppt", "pptx", "odp", "key")),
        KindRule(
            AttachmentKindName.Document,
            listOf("wordprocessing", "msword", "opendocument.text", "text/plain", "rtf", "markdown"),
            setOf("doc", "docx", "odt", "txt", "rtf", "pages", "md"),
        ),
        KindRule(AttachmentKindName.Calendar, listOf("text/calendar", "application/ics"), setOf("ics", "ifb")),
        KindRule(AttachmentKindName.Contact, listOf("vcard"), setOf("vcf")),
        KindRule(
            AttachmentKindName.Archive,
            listOf("zip", "x-7z", "x-rar", "x-tar", "gzip", "x-bzip", "x-xz"),
            setOf("zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz"),
        ),
        KindRule(AttachmentKindName.Audio, listOf("audio/"), setOf("mp3", "m4a", "wav", "ogg", "flac", "aac", "opus")),
        KindRule(AttachmentKindName.Video, listOf("video/"), setOf("mp4", "mov", "mkv", "webm", "avi", "m4v")),
        KindRule(
            AttachmentKindName.Code,
            listOf("json", "xml", "html", "javascript", "x-python", "x-sh", "css", "yaml"),
            setOf("json", "xml", "html", "htm", "js", "ts", "kt", "py", "sh", "css", "yaml", "yml", "sql"),
        ),
    )
