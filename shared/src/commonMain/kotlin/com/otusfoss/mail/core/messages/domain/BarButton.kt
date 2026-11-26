package com.otusfoss.mail.core.messages.domain

/**
 * What an action bar slot holds: a plain [MailAction], or an action the user completes with a
 * choice of their own, such as the folder of a [MoveTo]. [storageValue] names it in a saved bar,
 * and [target] decides which places can offer it. A [swipeable] button can also be a row's swipe.
 */
sealed interface BarButton {
    val storageValue: String
    val target: ActionTarget
    val swipeable: Boolean

    /**
     * A button for the folder at [path], the folder names from the top level down. Each account
     * finds the folder by that path ignoring case, as shared folder pages match them, so one
     * button works across accounts. Two buttons of one kind whose paths differ only in case are
     * the same button.
     */
    sealed class ForFolder(
        val path: List<String>,
        private val prefix: String,
    ) : BarButton {
        init {
            require(path.isNotEmpty() && path.none { it.isEmpty() })
        }

        /** [path] in lower case, as shared folders are keyed. */
        val key: List<String> = path.map { it.lowercase() }

        /** The folder's own name. */
        val name: String
            get() = path.last()

        override val storageValue: String
            get() = prefix + encodeParts(path)

        override fun equals(other: Any?): Boolean = other is ForFolder && other.prefix == prefix && other.key == key

        override fun hashCode(): Int = HASH_STEP * prefix.hashCode() + key.hashCode()

        override fun toString(): String = "$prefix$path"
    }

    /** Moves messages to the folder. Each message moves within its own account. */
    class MoveTo(
        path: List<String>,
    ) : ForFolder(path, MOVE_TO_PREFIX) {
        override val target: ActionTarget
            get() = ActionTarget.Messages

        override val swipeable: Boolean
            get() = true
    }

    /**
     * Adds messages to the folder and keeps them where they are, as a label. Once every message
     * has it, the button takes it off again.
     */
    class LabelAs(
        path: List<String>,
    ) : ForFolder(path, LABEL_PREFIX) {
        override val target: ActionTarget
            get() = ActionTarget.Messages

        override val swipeable: Boolean
            get() = true
    }

    /** Opens the folder's page, with the folder of every account that has one. */
    class GoTo(
        path: List<String>,
    ) : ForFolder(path, GO_TO_PREFIX) {
        override val target: ActionTarget
            get() = ActionTarget.List

        override val swipeable: Boolean
            get() = false
    }

    /** Turns one [filter] of the message list on and off. */
    data class Filter(
        val filter: ListFilter,
    ) : BarButton {
        override val target: ActionTarget
            get() = ActionTarget.List

        override val swipeable: Boolean
            get() = false

        override val storageValue: String
            get() = FILTER_PREFIX + filter.storageValue
    }

    /**
     * Starts a new message to [addresses], such as one person or a whole team. Two buttons whose
     * addresses differ only in case are the same button.
     */
    class WriteTo(
        val addresses: List<String>,
    ) : BarButton {
        init {
            require(addresses.isNotEmpty() && addresses.none { it.isBlank() })
        }

        private val key: List<String> = addresses.map { it.lowercase() }

        override val target: ActionTarget
            get() = ActionTarget.Anywhere

        override val swipeable: Boolean
            get() = false

        override val storageValue: String
            get() = WRITE_TO_PREFIX + encodeParts(addresses)

        override fun equals(other: Any?): Boolean = other is WriteTo && other.key == key

        override fun hashCode(): Int = key.hashCode()

        override fun toString(): String = "WriteTo($addresses)"
    }

    /**
     * Runs [steps] in order on the messages, as one button called [name]. A chain only turns things
     * on: it marks, flags, and labels, and may end with one step that files the messages; see
     * [isChain].
     */
    data class Chain(
        val name: String,
        val steps: List<BarButton>,
    ) : BarButton {
        init {
            require(name.isNotBlank() && steps.isChain())
        }

        override val target: ActionTarget
            get() = ActionTarget.Messages

        override val swipeable: Boolean
            get() = true

        override val storageValue: String
            get() = CHAIN_PREFIX + encodeParts(listOf(name) + steps.map { it.storageValue })
    }

    companion object {
        /** A saved button, such as `archive` or `move_to:Clients/Acme`; null when it is unknown. */
        fun fromStorageValue(value: String): BarButton? =
            when {
                value.startsWith(MOVE_TO_PREFIX) -> decodeParts(value, MOVE_TO_PREFIX)?.let(::MoveTo)
                value.startsWith(LABEL_PREFIX) -> decodeParts(value, LABEL_PREFIX)?.let(::LabelAs)
                value.startsWith(GO_TO_PREFIX) -> decodeParts(value, GO_TO_PREFIX)?.let(::GoTo)
                value.startsWith(FILTER_PREFIX) -> filterOf(value.removePrefix(FILTER_PREFIX))
                value.startsWith(WRITE_TO_PREFIX) -> decodeParts(value, WRITE_TO_PREFIX)?.let(::WriteTo)
                value.startsWith(CHAIN_PREFIX) -> decodeParts(value, CHAIN_PREFIX)?.let(::chainOf)
                else -> MailAction.fromStorageValue(value)
            }

        /** A saved swipe: "none" is no action, and a missing, unknown, or unswipeable value is [default]. */
        fun decodeSwipe(
            value: String?,
            default: BarButton?,
        ): BarButton? =
            when (value) {
                null -> default
                NO_SWIPE -> null
                else -> fromStorageValue(value)?.takeIf { it.swipeable } ?: default
            }

        fun encodeSwipe(button: BarButton?): String = button?.storageValue ?: NO_SWIPE

        private fun filterOf(name: String): Filter? = ListFilter.fromStorageValue(name)?.let(::Filter)

        /** The chain a saved name and its steps make; null when a step is unknown or they are no chain. */
        private fun chainOf(parts: List<String>): Chain? {
            val name = parts.first()
            val steps = parts.drop(1).map { fromStorageValue(it) ?: return null }
            return if (name.isNotBlank() && steps.isChain()) Chain(name, steps) else null
        }
    }
}

/** Whether a chain can run this step before it files the messages: it only turns something on. */
val BarButton.marksInChain: Boolean
    get() = this == MailAction.Read || this == MailAction.Flag || this is BarButton.LabelAs

/** Whether a chain can end with this step, which files the messages. */
val BarButton.filesInChain: Boolean
    get() = this is BarButton.MoveTo || this in FILING_STEPS

/**
 * Whether these steps make a chain: at least one, each once, with marks and labels first and at
 * most one step that files the messages, at the end.
 */
fun List<BarButton>.isChain(): Boolean =
    isNotEmpty() &&
        distinct() == this &&
        dropLast(1).all { it.marksInChain } &&
        last().let { it.marksInChain || it.filesInChain }

private val FILING_STEPS = setOf<BarButton>(MailAction.Archive, MailAction.Trash, MailAction.Spam)
private const val HASH_STEP = 31
private const val NO_SWIPE = "none"
