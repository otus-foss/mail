package com.otus.mail.feature.folders.domain

/** How Read later changes one message. */
enum class ReadLaterStep {
    /** Adds the Read later folder; the message stays in its other folders. */
    Save,

    /** Takes the message out of Read later; it stays in its other folders. */
    Remove,

    /** Moves the message into Read later, where the server allows no more folders per message. */
    Move,
}

/**
 * What Read later does for a message in [folderIds], shown in [shownId]. A message gains the folder
 * and stays where it is, unless the server's per-message limit is reached; then it moves. A message
 * must stay in at least one folder (RFC 8621, section 4.1.1), so it leaves Read later only when it
 * is in another folder too. Null without a Read later folder, or when the folder rights forbid it.
 */
fun AccountFolders.readLaterStep(
    folderIds: Set<String>,
    shownId: String,
): ReadLaterStep? {
    val target = readLater ?: return null
    val roomForMore = limits.maxFoldersPerMessage?.let { folderIds.size < it } ?: true
    return when {
        target.id in folderIds -> {
            ReadLaterStep.Remove.takeIf { target.rights.mayRemoveItems && (folderIds - target.id).isNotEmpty() }
        }

        !target.rights.mayAddItems -> {
            null
        }

        roomForMore -> {
            ReadLaterStep.Save
        }

        else -> {
            ReadLaterStep.Move.takeIf { folder(shownId)?.rights?.mayRemoveItems != false }
        }
    }
}
