package com.otus.mail.app

import com.otus.avatar.AvatarPhotoPicker
import com.otus.mail.feature.inbox.domain.AttachmentFiles
import com.otus.mail.feature.inbox.domain.MessagePrinter
import com.otus.mail.feature.inbox.domain.SavedFiles
import com.otus.mail.feature.inbox.domain.SignatureImages

/**
 * The platform's pickers and file handling: [signatureImages] picks and shrinks signature images,
 * [attachmentFiles] picks files to attach, [savedFiles] saves and opens received files, and
 * [printer] prints a message, and [avatarPhotos] picks the photo of one of the user's addresses.
 * Each is null where the platform has none.
 */
class MailPickers(
    internal val signatureImages: SignatureImages? = null,
    internal val attachmentFiles: AttachmentFiles? = null,
    internal val savedFiles: SavedFiles? = null,
    internal val printer: MessagePrinter? = null,
    internal val avatarPhotos: AvatarPhotoPicker? = null,
)
