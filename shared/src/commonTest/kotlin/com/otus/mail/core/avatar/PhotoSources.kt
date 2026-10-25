package com.otus.mail.core.avatar

import com.otus.avatar.AvatarImage
import com.otus.avatar.AvatarRequest
import com.otus.avatar.ContactAvatar
import com.otus.avatar.ContactAvatarSource

/** A contact source whose cards hold only photos, as [load] returns them. */
internal fun photoSource(load: suspend (AvatarRequest, Boolean) -> AvatarImage?): ContactAvatarSource =
    ContactAvatarSource { request, external -> load(request, external)?.let { ContactAvatar.Photo(it, "image/png") } }
