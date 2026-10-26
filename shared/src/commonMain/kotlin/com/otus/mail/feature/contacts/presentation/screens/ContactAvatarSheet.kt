package com.otus.mail.feature.contacts.presentation.screens

import androidx.compose.runtime.Composable
import com.otus.accounts.presentation.components.AvatarSheet
import com.otus.avatar.ContactAvatar
import com.otus.avatar.ResolvedAvatar
import com.otus.mail.feature.contacts.presentation.ContactsState

/**
 * The avatar editor for a contact named [name], the same one the user's own addresses use: a
 * photo, the website icon of [address]'s domain, or a color with an icon or letters. It starts
 * from [look] and runs [onSave] with the new avatar, or null to go back to initials.
 */
@Composable
internal fun ContactAvatarSheet(
    name: String,
    address: String?,
    look: ResolvedAvatar,
    state: ContactsState,
    onSave: (ContactAvatar?) -> Unit,
    onDismiss: () -> Unit,
) {
    AvatarSheet(
        name,
        address?.substringAfterLast('@').orEmpty(),
        look,
        pickPhoto = if (state.canPickPhoto) state::pickPhoto else null,
        onSave = onSave,
        onDismiss = onDismiss,
    )
}
