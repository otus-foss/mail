package com.otus.mail.feature.inbox.presentation.read

import com.otus.mail.core.text.sizeText
import com.otus.mail.feature.inbox.domain.MailAttachment

/** What kind of file an attachment is, which picks its icon and the word its detail line uses. */
enum class AttachmentKindName(
    val label: String?,
) {
    Image("Image"),
    Pdf("PDF"),
    Sheet("Spreadsheet"),
    Document("Document"),

    /** Named by its extension instead, as in "ZIP". */
    Other(null),
}

/** The kind of this file, by its media type, then by its name's extension. */
fun MailAttachment.kind(): AttachmentKindName {
    val type = type.lowercase()
    return when {
        type.startsWith("image/") || extension in IMAGE_EXTENSIONS -> AttachmentKindName.Image
        type == "application/pdf" || extension == "pdf" -> AttachmentKindName.Pdf
        SHEET_TYPES.any(type::contains) || extension in SHEET_EXTENSIONS -> AttachmentKindName.Sheet
        DOCUMENT_TYPES.any(type::contains) || extension in DOCUMENT_EXTENSIONS -> AttachmentKindName.Document
        else -> AttachmentKindName.Other
    }
}

/** The line under the file's name: its kind and size, as in "PDF · 240 KB" or "ZIP · 1 MB". */
fun MailAttachment.detail(): String {
    val kind = kind().label ?: extension.uppercase().ifBlank { "File" }
    return "$kind · ${sizeText(size)}"
}

/** The attachment strip's line: how many files and their total size, as in "3 attachments · 4.2 MB". */
fun attachmentSummary(files: List<MailAttachment>): String {
    val count = if (files.size == 1) "1 attachment" else "${files.size} attachments"
    return "$count · ${sizeText(files.sumOf { it.size })}"
}

private val MailAttachment.extension: String
    get() = name.substringAfterLast('.', "").lowercase()

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp", "svg")
private val SHEET_TYPES = listOf("spreadsheet", "excel", "text/csv")
private val SHEET_EXTENSIONS = setOf("xls", "xlsx", "ods", "csv", "numbers")
private val DOCUMENT_TYPES = listOf("wordprocessing", "msword", "opendocument.text", "text/plain", "rtf")
private val DOCUMENT_EXTENSIONS = setOf("doc", "docx", "odt", "txt", "rtf", "pages", "md")
