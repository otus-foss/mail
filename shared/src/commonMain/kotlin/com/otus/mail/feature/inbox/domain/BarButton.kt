package com.otus.mail.feature.inbox.domain

/**
 * What an action bar slot holds: a plain [MailAction], or an action with a target the user
 * picked, such as [MoveTo] a folder. [storageValue] names it in a saved bar, and [target] decides
 * which places can offer it.
 */
sealed interface BarButton {
    val storageValue: String
    val target: ActionTarget

    /**
     * Moves messages to the folder at [path], the folder names from the top level down. Each
     * message moves within its own account, to the folder whose path matches ignoring case, as
     * shared folder pages match them, so one button works across accounts. Two buttons whose paths
     * differ only in case are the same button.
     */
    class MoveTo(
        val path: List<String>,
    ) : BarButton {
        init {
            require(path.isNotEmpty() && path.none { it.isEmpty() })
        }

        /** [path] in lower case, as shared folders are keyed. */
        val key: List<String> = path.map { it.lowercase() }

        /** The folder's own name. */
        val name: String
            get() = path.last()

        override val target: ActionTarget
            get() = ActionTarget.Messages

        override val storageValue: String
            get() = MOVE_TO_PREFIX + path.joinToString(PATH_SEPARATOR) { it.escaped() }

        override fun equals(other: Any?): Boolean = other is MoveTo && other.key == key

        override fun hashCode(): Int = key.hashCode()

        override fun toString(): String = "MoveTo($path)"
    }

    companion object {
        /** A saved button, such as `archive` or `move_to:Clients/Acme`; null when it is unknown. */
        fun fromStorageValue(value: String): BarButton? {
            if (!value.startsWith(MOVE_TO_PREFIX)) return MailAction.fromStorageValue(value)
            val path = value.removePrefix(MOVE_TO_PREFIX).split(PATH_SEPARATOR).map { it.unescaped() }
            return path.takeIf { names -> names.none { it.isEmpty() } }?.let(::MoveTo)
        }
    }
}

private const val MOVE_TO_PREFIX = "move_to:"
private const val PATH_SEPARATOR = "/"

// Folder names may hold the characters a saved bar and a path use as separators.
private val ESCAPES = mapOf('%' to "%25", '/' to "%2F", ',' to "%2C", '|' to "%7C")

private fun String.escaped(): String = map { ESCAPES[it] ?: it.toString() }.joinToString("")

private fun String.unescaped(): String =
    ESCAPE.replace(this) {
        it.groupValues[1]
            .toInt(HEX)
            .toChar()
            .toString()
    }

private const val HEX = 16
private val ESCAPE = Regex("%([0-9A-Fa-f]{2})")
