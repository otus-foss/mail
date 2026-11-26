package com.otusfoss.mail.core.messages.domain

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

    /** A message waiting in Scheduled. No bar offers these to pick; the Scheduled folder's bar has them. */
    Scheduled,
}

/**
 * One thing the user can do in Mail, from a swipe, an action bar slot, or a page's menu. Only
 * [swipeable] actions can be a swipe; [target] decides where the bar can offer it.
 */
enum class MailAction(
    override val storageValue: String,
    override val target: ActionTarget,
    override val swipeable: Boolean = false,
) : BarButton {
    Read("read", ActionTarget.Messages, swipeable = true),
    Flag("flag", ActionTarget.Messages, swipeable = true),
    ReadLater("read_later", ActionTarget.Messages, swipeable = true),
    Archive("archive", ActionTarget.Messages, swipeable = true),
    Trash("trash", ActionTarget.Messages, swipeable = true),
    Spam("spam", ActionTarget.Messages, swipeable = true),
    NotSpam("not_spam", ActionTarget.Messages),
    Restore("restore", ActionTarget.Messages),
    DeleteForever("delete_forever", ActionTarget.Messages),
    Move("move", ActionTarget.Messages, swipeable = true),
    SuggestedMove("suggested_move", ActionTarget.Messages),
    RuleForSender("rule_for_sender", ActionTarget.OneMessage),
    Reply("reply", ActionTarget.OneMessage),
    ReplyAll("reply_all", ActionTarget.OneMessage),
    Forward("forward", ActionTarget.OneMessage),
    FollowUp("follow_up", ActionTarget.OneMessage),

    /** Leaves the mailing list the open message came from. */
    Unsubscribe("unsubscribe", ActionTarget.OneMessage),

    /** Stops alerts for the open message's conversation, or brings them back. */
    Mute("mute", ActionTarget.OneMessage),
    BlockSender("block_sender", ActionTarget.OneMessage),

    /** Shows the open message's blocked remote images this time, or hides them again. */
    ShowImages("show_images", ActionTarget.OneMessage),
    NewMail("new_mail", ActionTarget.Anywhere),
    Search("search", ActionTarget.List),
    Filters("filters", ActionTarget.List),
    MarkAllRead("mark_all_read", ActionTarget.List),

    /** Selects every message the list shows, so the selection's bar can act on them. */
    SelectAll("select_all", ActionTarget.List),
    EmptyFolder("empty_folder", ActionTarget.List),
    Send("send", ActionTarget.Draft),

    /** Picks when Send has the server send the draft. */
    ScheduleSend("schedule_send", ActionTarget.Draft),
    Attach("attach", ActionTarget.Draft),
    Discard("discard", ActionTarget.Draft),
    EditScheduled("edit_scheduled", ActionTarget.Scheduled),
    SendNow("send_now", ActionTarget.Scheduled),

    /** Lists every action the screen offers in a menu, so a bar can reach those it leaves out. */
    AllActions("all_actions", ActionTarget.Anywhere),
    ;

    companion object {
        fun fromStorageValue(value: String?): MailAction? = entries.firstOrNull { it.storageValue == value }
    }
}
