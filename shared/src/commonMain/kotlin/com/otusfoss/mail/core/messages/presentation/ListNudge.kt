package com.otusfoss.mail.core.messages.presentation

import com.otusfoss.core.designsystem.theme.colors.FolderColor

/**
 * The folder color nudge above a list's first row. [folderName] has no color yet; [onPick] gives it
 * one of [colors], and [onDismiss] closes the nudge for good.
 */
class ListNudge(
    val folderName: String,
    val colors: List<FolderColor>,
    val onPick: (FolderColor) -> Unit,
    val onDismiss: () -> Unit,
)
