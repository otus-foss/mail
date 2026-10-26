package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import com.otus.accounts.presentation.components.AvatarSheet
import com.otus.avatar.ContactAvatar
import com.otus.avatar.ResolvedAvatar

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
