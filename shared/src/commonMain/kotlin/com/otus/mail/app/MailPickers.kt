package com.otus.mail.app

import com.otus.mail.feature.inbox.domain.AttachmentFiles
import com.otus.mail.feature.inbox.domain.SignatureImages

/**
 * The platform's pickers: [signatureImages] picks and shrinks signature images, and
 * [attachmentFiles] picks files to attach. Each is null where the platform has no picker.
 */
class MailPickers(
    internal val signatureImages: SignatureImages? = null,
    internal val attachmentFiles: AttachmentFiles? = null,
)
