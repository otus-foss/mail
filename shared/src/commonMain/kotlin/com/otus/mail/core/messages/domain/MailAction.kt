package com.otus.mail.core.messages.domain

import com.otus.mail.core.settings.domain.storageValue

/** What an action works on, which decides the places that can offer it. */
enum class ActionTarget {
    /** One message or a selection. */
    Messages,

    /** A single message, such as the one a reply answers. */
    OneMessage,

    /** The list of messages on screen. */
    List,

    /** The draft being written. */
    Draft,

    /** Nothing in particular; it works on every screen. */
    Anywhere,
}

/**
 * One thing the user can do in Mail, from a swipe, an action bar slot, or a page's menu. Only
 * [swipeable] actions can be a swipe; [target] decides where the bar can offer it.
 */
enum class MailAction(
    override val storageValue: String,
    override val target: ActionTarget,
    val swipeable: Boolean = false,
) : BarButton {
    Read("read", ActionTarget.Messages, swipeable = true),
    Flag("flag", ActionTarget.Messages, swipeable = true),
    ReadLater("read_later", ActionTarget.Messages, swipeable = true),
    Archive("archive", ActionTarget.Messages, swipeable = true),
    Trash("trash", ActionTarget.Messages, swipeable = true),
    Spam("spam", ActionTarget.Messages),
    NotSpam("not_spam", ActionTarget.Messages),
    Restore("restore", ActionTarget.Messages),
    DeleteForever("delete_forever", ActionTarget.Messages),
    Move("move", ActionTarget.Messages),
    SuggestedMove("suggested_move", ActionTarget.Messages),
    RuleForSender("rule_for_sender", ActionTarget.OneMessage),
    Reply("reply", ActionTarget.OneMessage),
    ReplyAll("reply_all", ActionTarget.OneMessage),
    Forward("forward", ActionTarget.OneMessage),
    FollowUp("follow_up", ActionTarget.OneMessage),
    NewMail("new_mail", ActionTarget.Anywhere),
    Search("search", ActionTarget.List),
    Filters("filters", ActionTarget.List),
    MarkAllRead("mark_all_read", ActionTarget.List),
    EmptyFolder("empty_folder", ActionTarget.List),
    Send("send", ActionTarget.Draft),
    Attach("attach", ActionTarget.Draft),
    Discard("discard", ActionTarget.Draft),

    /** Lists every action the screen offers in a menu, so a bar can reach those it leaves out. */
    AllActions("all_actions", ActionTarget.Anywhere),
    ;

    companion object {
        fun fromStorageValue(value: String?): MailAction? = entries.firstOrNull { it.storageValue == value }

        /** A saved swipe: "none" is no action, and a missing or unknown value is [default]. */
        fun decodeSwipe(
            value: String?,
            default: MailAction?,
        ): MailAction? =
            when (value) {
                null -> default
                NO_SWIPE -> null
                else -> fromStorageValue(value)?.takeIf { it.swipeable } ?: default
            }

        fun encodeSwipe(action: MailAction?): String = action?.storageValue ?: NO_SWIPE

        private const val NO_SWIPE = "none"
    }
}
