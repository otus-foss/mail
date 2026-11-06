package com.otus.mail.core.messages.domain

import com.otus.mail.core.messages.domain.MailAction.Archive
import com.otus.mail.core.messages.domain.MailAction.Attach
import com.otus.mail.core.messages.domain.MailAction.Discard
import com.otus.mail.core.messages.domain.MailAction.Filters
import com.otus.mail.core.messages.domain.MailAction.Flag
import com.otus.mail.core.messages.domain.MailAction.MarkAllRead
import com.otus.mail.core.messages.domain.MailAction.Move
import com.otus.mail.core.messages.domain.MailAction.NewMail
import com.otus.mail.core.messages.domain.MailAction.Read
import com.otus.mail.core.messages.domain.MailAction.Reply
import com.otus.mail.core.messages.domain.MailAction.Search
import com.otus.mail.core.messages.domain.MailAction.Send
import com.otus.mail.core.messages.domain.MailAction.Trash
import com.otus.mail.core.settings.domain.storageValue

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

    /** The plain actions the bar here can offer. */
    val actions: List<MailAction>
        get() = MailAction.entries.filter(::offers)

    /** Whether the bar here can hold [button]; every bar can hold All actions. */
    fun offers(button: BarButton): Boolean = button == MailAction.AllActions || button.target in targets

    /** Places with their own bar in Adaptive mode. */
    val adaptive: Boolean
        get() = this != Everywhere

    /** The spec's layouts. Reply opens a draft, and Send sends it from the compose page. */
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

/** A place in an [ActionBarLayout]: its primary, or the secondary slot at [index]. */
sealed interface BarSlot {
    data object Primary : BarSlot

    data class Secondary(
        val index: Int,
    ) : BarSlot
}

/** Up to [MAX_SECONDARY] distinct [secondary] buttons, in slot order, beside one [primary] that is none of them. */
data class ActionBarLayout(
    val secondary: List<BarButton>,
    val primary: BarButton,
) {
    init {
        require(secondary.size <= MAX_SECONDARY && secondary.distinct() == secondary && primary !in secondary)
    }

    /** The primary, then the secondary actions in slot order, as settings list them. */
    val buttons: List<BarButton>
        get() = listOf(primary) + secondary

    /**
     * Puts [action] in [slot], or empties it when null; later slots then move up. An action
     * already in another slot or the primary trades places with the slot's old one, which an
     * empty slot cannot do for the primary.
     */
    fun withSlot(
        slot: Int,
        action: BarButton?,
    ): ActionBarLayout {
        val old = secondary.getOrNull(slot)
        return when {
            action == null -> copy(secondary = secondary - listOfNotNull(old))
            action == primary -> swapPrimary(old, action)
            old == null -> copy(secondary = secondary - action + action)
            else -> copy(secondary = secondary.swapping(old, action))
        }
    }

    private fun swapPrimary(
        old: BarButton?,
        action: BarButton,
    ): ActionBarLayout = old?.let { ActionBarLayout(secondary.replace(it, action), it) } ?: this

    /**
     * Drops [action], such as Read later once it is off. A dropped primary returns to [place]'s
     * default, or to the first other action the place offers.
     */
    fun without(
        action: BarButton,
        place: ActionBarPlace,
    ): ActionBarLayout {
        val kept = secondary - action
        if (primary != action) return copy(secondary = kept)
        val nextPrimary =
            place.defaultLayout.primary.takeIf { it != action }
                ?: kept.firstOrNull()
                ?: place.actions.first { it != action }
        return ActionBarLayout(kept - nextPrimary, nextPrimary)
    }

    /** The button in [slot], or null for an empty secondary slot. */
    fun actionAt(slot: BarSlot): BarButton? =
        when (slot) {
            BarSlot.Primary -> primary
            is BarSlot.Secondary -> secondary.getOrNull(slot.index)
        }

    /** Where [action] sits in this bar, if anywhere. */
    fun slotOf(action: BarButton): BarSlot? =
        when (action) {
            primary -> BarSlot.Primary
            in secondary -> BarSlot.Secondary(secondary.indexOf(action))
            else -> null
        }

    /**
     * Puts [action] in [slot], such as a Move to button for another folder; an action already
     * elsewhere in the bar trades places with the slot's old one.
     */
    fun with(
        slot: BarSlot,
        action: BarButton,
    ): ActionBarLayout =
        when (slot) {
            BarSlot.Primary -> copy(secondary = secondary.replace(action, primary), primary = action)
            is BarSlot.Secondary -> withSlot(slot.index, action)
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
            val actions = names.mapNotNull(BarButton::fromStorageValue).filter(place::offers)
            val valid = actions.size == names.size && actions.size <= MAX_SECONDARY + 1 && actions.distinct() == actions
            return if (valid) ActionBarLayout(actions.dropLast(1), actions.last()) else default
        }
    }
}

private fun <T> List<T>.replace(
    old: T,
    new: T,
): List<T> = map { if (it == old) new else it }

private fun List<BarButton>.swapping(
    first: BarButton,
    second: BarButton,
): List<BarButton> =
    map {
        when (it) {
            first -> second
            second -> first
            else -> it
        }
    }
