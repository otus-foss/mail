package com.otusfoss.mail.testing

import com.otusfoss.core.avatar.AvatarImage
import com.otusfoss.core.avatar.AvatarRequest
import com.otusfoss.core.avatar.ContactAvatar
import com.otusfoss.core.avatar.ContactAvatarSource

/** A contact source whose cards hold only photos, as [load] returns them. */
internal fun photoSource(load: suspend (AvatarRequest, Boolean) -> AvatarImage?): ContactAvatarSource =
    ContactAvatarSource { request, external -> load(request, external)?.let { ContactAvatar.Photo(it, "image/png") } }
