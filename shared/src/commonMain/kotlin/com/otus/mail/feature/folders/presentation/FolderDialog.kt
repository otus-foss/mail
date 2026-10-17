package com.otus.mail.feature.folders.presentation

import androidx.compose.runtime.saveable.Saver

/** The open folder popup. It names folders by id, so it survives reloads and saved state. */
sealed interface FolderDialog {
    val accountId: String

    /** A new folder under [parentId], or at the top level when it is null. */
    data class Create(
        override val accountId: String,
        val parentId: String?,
    ) : FolderDialog

    data class Rename(
        override val accountId: String,
        val folderId: String,
    ) : FolderDialog

    data class Delete(
        override val accountId: String,
        val folderId: String,
    ) : FolderDialog

    /** The folder the folder moves into, or the top level. */
    data class Move(
        override val accountId: String,
        val folderId: String,
    ) : FolderDialog

    /** The folder's icon color, kept on this device. */
    data class Color(
        override val accountId: String,
        val folderId: String,
    ) : FolderDialog

    companion object {
        val Saver: Saver<FolderDialog?, List<String>> =
            Saver(
                save = { dialog ->
                    when (dialog) {
                        is Create -> listOf(CREATE, dialog.accountId, dialog.parentId.orEmpty())
                        is Rename -> listOf(RENAME, dialog.accountId, dialog.folderId)
                        is Delete -> listOf(DELETE, dialog.accountId, dialog.folderId)
                        is Color -> listOf(COLOR, dialog.accountId, dialog.folderId)
                        is Move -> listOf(MOVE, dialog.accountId, dialog.folderId)
                        null -> null
                    }
                },
                restore = { (kind, accountId, target) ->
                    when (kind) {
                        CREATE -> Create(accountId, target.ifEmpty { null })
                        RENAME -> Rename(accountId, target)
                        DELETE -> Delete(accountId, target)
                        COLOR -> Color(accountId, target)
                        MOVE -> Move(accountId, target)
                        else -> null
                    }
                },
            )

        // Fixed keys: release builds rename classes.
        private const val CREATE = "create"
        private const val RENAME = "rename"
        private const val DELETE = "delete"
        private const val COLOR = "color"
        private const val MOVE = "move"
    }
}
