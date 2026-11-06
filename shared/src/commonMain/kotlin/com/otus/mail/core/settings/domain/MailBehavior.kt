package com.otus.mail.core.settings.domain

import com.otus.mail.core.messages.domain.MailAction

/**
 * Every saved Mail setting, grouped by the feature that reads it. [readLater] adds a Read later
 * folder to each account, an action that saves mail there, and its pill, so it reaches the list,
 * the actions, and the sidebar alike.
 */
data class MailBehavior(
    val readLater: Boolean = false,
    val list: ListBehavior = ListBehavior(InboxPill.defaults(readLater)),
    val read: ReadBehavior = ReadBehavior(),
    val write: WriteBehavior = WriteBehavior(),
    val actions: ActionBehavior = ActionBehavior(),
    val avatars: AvatarBehavior = AvatarBehavior(),
    val privacy: PrivacyBehavior = PrivacyBehavior(),
    val sidebar: SidebarBehavior = SidebarBehavior(),
) {
    /**
     * Turning Read later on adds its pill to the end of the row. Turning it off removes the pill,
     * returns a swipe set to it to that swipe's default, and takes it out of every bar.
     */
    fun withReadLater(on: Boolean): MailBehavior {
        val defaults = ActionBehavior()

        fun MailAction?.orDefault(default: MailAction?) = if (!on && this == MailAction.ReadLater) default else this
        return copy(
            readLater = on,
            list =
                list.copy(
                    pills =
                        when {
                            !on -> list.pills - InboxPill.ReadLater
                            InboxPill.ReadLater in list.pills -> list.pills
                            else -> list.pills + InboxPill.ReadLater
                        },
                ),
            actions =
                actions.copy(
                    startToEnd = actions.startToEnd.orDefault(defaults.startToEnd),
                    endToStart = actions.endToStart.orDefault(defaults.endToStart),
                    bars =
                        if (on) {
                            actions.bars
                        } else {
                            actions.bars.mapValues { (place, layout) -> layout.without(MailAction.ReadLater, place) }
                        },
                ),
        )
    }
}

/** App-private storage for [MailBehavior]. Unknown or missing values read as the defaults. */
interface MailBehaviorStore {
    fun read(): MailBehavior

    fun write(behavior: MailBehavior)
}
