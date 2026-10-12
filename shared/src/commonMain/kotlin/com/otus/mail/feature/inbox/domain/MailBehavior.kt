package com.otus.mail.feature.inbox.domain

/**
 * How the inbox responds to the user. Swipes are named by layout direction: [startToEnd] is a
 * swipe right in left-to-right languages. [readLater] adds a Read later folder to each account, an
 * action that saves mail there, and its pill. [pills] are the inbox row's pills, in order.
 * [sidebar] arranges the sidebar. [actionBar] holds each place's bar, and [actionBarMode] picks
 * whether places use their own or the Everywhere bar. A null swipe does nothing.
 */
data class MailBehavior(
    val startToEnd: MailAction? = MailAction.Read,
    val endToStart: MailAction? = MailAction.Archive,
    val markReadOnOpen: Boolean = true,
    val readLater: Boolean = false,
    val pills: List<InboxPill> = InboxPill.defaults(readLater),
    val sidebar: SidebarLayout = SidebarLayout(),
    val actionBar: Map<ActionBarPlace, ActionBarLayout> = ActionBarPlace.entries.associateWith { it.defaultLayout },
    val actionBarMode: ActionBarMode = ActionBarMode.Adaptive,
) {
    /** What a swipe can be set to besides nothing; Read later only while it is on. */
    val swipeChoices: List<MailAction>
        get() = MailAction.entries.filter { it.swipeable && offers(it) }

    /** What a bar slot at [place] can hold; Read later only while it is on. */
    fun barChoices(place: ActionBarPlace): List<MailAction> = place.actions.filter(::offers)

    /** The layout [place] shows: its own in Adaptive mode, the Everywhere one in Fixed mode. */
    fun barLayout(place: ActionBarPlace): ActionBarLayout {
        val shown = if (actionBarMode == ActionBarMode.Fixed) ActionBarPlace.Everywhere else place
        return savedLayout(shown)
    }

    /** The layout saved for [place], whichever mode is on; settings edit this one. */
    fun savedLayout(place: ActionBarPlace): ActionBarLayout = actionBar[place] ?: place.defaultLayout

    private fun offers(action: MailAction): Boolean = readLater || action != MailAction.ReadLater

    /**
     * Turning Read later on adds its pill to the end of the row. Turning it off removes the pill,
     * returns a swipe set to it to that swipe's default, and takes it out of every bar.
     */
    fun withReadLater(on: Boolean): MailBehavior {
        val defaults = MailBehavior()

        fun MailAction?.orDefault(default: MailAction?) =
            if (!on &&
                this == MailAction.ReadLater
            ) {
                default
            } else {
                this
            }
        return copy(
            readLater = on,
            startToEnd = startToEnd.orDefault(defaults.startToEnd),
            endToStart = endToStart.orDefault(defaults.endToStart),
            pills =
                when {
                    !on -> pills - InboxPill.ReadLater
                    InboxPill.ReadLater in pills -> pills
                    else -> pills + InboxPill.ReadLater
                },
            actionBar =
                if (on) {
                    actionBar
                } else {
                    actionBar.mapValues { (place, layout) ->
                        layout.without(MailAction.ReadLater, place)
                    }
                },
        )
    }
}

/** App-private storage for [MailBehavior]. Unknown or missing values read as the defaults. */
interface MailBehaviorStore {
    fun read(): MailBehavior

    fun write(behavior: MailBehavior)
}
