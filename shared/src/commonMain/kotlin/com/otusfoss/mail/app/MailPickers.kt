package com.otusfoss.mail.app

import com.otusfoss.core.avatar.AvatarPhotoPicker
import com.otusfoss.mail.core.files.AttachmentFiles
import com.otusfoss.mail.core.files.SavedFiles
import com.otusfoss.mail.core.messages.domain.SignatureImages
import com.otusfoss.mail.feature.read.domain.MessagePrinter

/**
 * The platform's pickers and file handling: [signatureImages] picks and shrinks signature images,
 * [attachmentFiles] picks files to attach, [savedFiles] saves and opens received files, and
 * [printer] prints a message, and [avatarPhotos] picks the photo of one of the user's addresses or of a contact.
 * Each is null where the platform has none.
 */
class MailPickers(
    internal val signatureImages: SignatureImages? = null,
    internal val attachmentFiles: AttachmentFiles? = null,
    internal val savedFiles: SavedFiles? = null,
    internal val printer: MessagePrinter? = null,
    internal val avatarPhotos: AvatarPhotoPicker? = null,
)
