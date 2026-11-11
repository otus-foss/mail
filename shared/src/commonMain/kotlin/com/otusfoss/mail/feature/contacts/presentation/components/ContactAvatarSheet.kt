package com.otusfoss.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.avatar.ContactAvatar
import com.otusfoss.core.avatar.ResolvedAvatar
import com.otusfoss.core.avatar.ui.presentation.components.AvatarSheet

/**
 * The avatar editor for a contact named [name], the same one the user's own addresses use: a
 * photo, the website icon of [address]'s domain, or a color with an icon or letters. It starts
 * from [look] and runs [onSave] with the new avatar, or null to go back to initials. [pickPhoto] is
 * null where photos cannot be picked.
 */
@Composable
internal fun ContactAvatarSheet(
    name: String,
    address: String?,
    look: ResolvedAvatar,
    pickPhoto: (suspend () -> ContactAvatar.Photo?)?,
    onSave: (ContactAvatar?) -> Unit,
    onDismiss: () -> Unit,
) {
    AvatarSheet(
        name,
        address?.substringAfterLast('@').orEmpty(),
        look,
        pickPhoto = pickPhoto,
        onSave = onSave,
        onDismiss = onDismiss,
    )
}
