package com.otus.mail.feature.inbox.domain

import com.otus.mail.feature.inbox.domain.MailAction.Archive
import com.otus.mail.feature.inbox.domain.MailAction.Attach
import com.otus.mail.feature.inbox.domain.MailAction.Discard
import com.otus.mail.feature.inbox.domain.MailAction.Filters
import com.otus.mail.feature.inbox.domain.MailAction.Flag
import com.otus.mail.feature.inbox.domain.MailAction.MarkAllRead
import com.otus.mail.feature.inbox.domain.MailAction.Move
import com.otus.mail.feature.inbox.domain.MailAction.NewMail
import com.otus.mail.feature.inbox.domain.MailAction.Read
import com.otus.mail.feature.inbox.domain.MailAction.Reply
import com.otus.mail.feature.inbox.domain.MailAction.Search
import com.otus.mail.feature.inbox.domain.MailAction.Send
import com.otus.mail.feature.inbox.domain.MailAction.Trash

/** Whether each place has its own bar, or one bar is used everywhere. */
enum class ActionBarMode(
    val storageValue: String,
) {
    /** Each place shows the bar chosen for it. */
    Adaptive("adaptive"),

    /** Every place shows the [ActionBarPlace.Everywhere] bar; actions that do not apply there are disabled. */
    Fixed("fixed"),
    ;

    companion object {
        fun fromStorageValue(value: String?): ActionBarMode? = entries.firstOrNull { it.storageValue == value }
    }
}

/** A screen with an action bar, plus [Everywhere], the one bar Fixed mode uses on all of them. */
enum class ActionBarPlace(
    val storageValue: String,
    private val targets: Set<ActionTarget>,
) {
    /** A list of messages, such as the inbox or a folder, with nothing selected. */
    Inbox("inbox", setOf(ActionTarget.List, ActionTarget.Anywhere)),

    /** Messages selected in a list. */
    Selection("selection", setOf(ActionTarget.Messages)),

    /** An open message. */
    Message("message", setOf(ActionTarget.Messages, ActionTarget.OneMessage, ActionTarget.Anywhere)),

    /** A new message, reply, or forward being written. */
    Compose("compose", setOf(ActionTarget.Draft)),

    /** The layout Fixed mode shows on every screen. */
    Everywhere("everywhere", ActionTarget.entries.toSet()),
    ;

    /** What the bar here can offer. */
    val actions: List<MailAction>
        get() = MailAction.entries.filter { it.target in targets }

    /** Places with their own bar in Adaptive mode. */
    val adaptive: Boolean
        get() = this != Everywhere

    /** The spec's layouts; until Mail can send, Reply opens a draft and Send stays disabled. */
    val defaultLayout: ActionBarLayout
        get() =
            when (this) {
                Inbox -> ActionBarLayout(listOf(Search, Filters, MarkAllRead), NewMail)
                Selection -> ActionBarLayout(listOf(Read, Flag, Trash), Move)
                Message -> ActionBarLayout(listOf(Archive, Trash, Move), Reply)
                Compose -> ActionBarLayout(listOf(Attach, Discard), Send)
                Everywhere -> ActionBarLayout(listOf(Search, Archive, Trash), NewMail)
            }
}

/** Up to [MAX_SECONDARY] distinct [secondary] actions, in slot order, beside one [primary] that is none of them. */
data class ActionBarLayout(
    val secondary: List<MailAction>,
    val primary: MailAction,
) {
    init {
        require(secondary.size <= MAX_SECONDARY && secondary.distinct() == secondary && primary !in secondary)
    }

    /** Makes [action] the primary; when it was a secondary, the old primary takes its slot. */
    fun withPrimary(action: MailAction): ActionBarLayout =
        copy(secondary = secondary.replace(action, primary), primary = action)

    /**
     * Puts [action] in [slot], or empties it when null; later slots then move up. An action
     * already in another slot or the primary trades places with the slot's old one, which an
     * empty slot cannot do for the primary.
     */
    fun withSlot(
        slot: Int,
        action: MailAction?,
    ): ActionBarLayout {
        val old = secondary.getOrNull(slot)
        return when {
            action == null -> {
                copy(secondary = secondary - listOfNotNull(old))
            }

            action == primary -> {
                if (old == null) this else ActionBarLayout(secondary.replace(old, action), old)
            }

            old == null -> {
                copy(secondary = secondary - action + action)
            }

            else -> {
                copy(
                    secondary =
                        secondary.map {
                            if (it == old) {
                                action
                            } else if (it == action) {
                                old
                            } else {
                                it
                            }
                        },
                )
            }
        }
    }

    /**
     * Drops [action], such as Read later once it is off. A dropped primary returns to [place]'s
     * default, or to the first other action the place offers.
     */
    fun without(
        action: MailAction,
        place: ActionBarPlace,
    ): ActionBarLayout {
        val kept = secondary - action
        if (primary != action) return copy(secondary = kept)
        val primary =
            place.defaultLayout.primary.takeIf { it != action }
                ?: kept.firstOrNull()
                ?: place.actions.first { it != action }
        return ActionBarLayout(kept - primary, primary)
    }

    fun encode(): String = secondary.joinToString(",") { it.storageValue } + "|" + primary.storageValue

    companion object {
        const val MAX_SECONDARY = 3

        /** A saved layout for [place]; anything it cannot offer, or a malformed value, reads as the default. */
        fun decode(
            value: String?,
            place: ActionBarPlace,
        ): ActionBarLayout {
            val default = place.defaultLayout
            val parts = value?.split("|")?.takeIf { it.size == 2 } ?: return default
            val names = parts[0].split(",").filter { it.isNotEmpty() } + parts[1]
            val actions = names.mapNotNull(MailAction::fromStorageValue).filter { it in place.actions }
            val valid = actions.size == names.size && actions.size <= MAX_SECONDARY + 1 && actions.distinct() == actions
            return if (valid) ActionBarLayout(actions.dropLast(1), actions.last()) else default
        }
    }
}

private fun <T> List<T>.replace(
    old: T,
    new: T,
): List<T> = map { if (it == old) new else it }
