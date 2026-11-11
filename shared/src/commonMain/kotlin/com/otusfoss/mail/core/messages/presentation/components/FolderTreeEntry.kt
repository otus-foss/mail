package com.otusfoss.mail.core.messages.presentation.components

/**
 * One of the user's own folders in a sheet's folder tree: its [name], its [color], [depth] levels
 * in, and an optional grey [description] under it, such as the parents of a folder whose parent is
 * not listed.
 */
internal data class FolderTreeEntry(
    val name: String,
    val color: String?,
    val depth: Int,
    val description: String? = null,
)
