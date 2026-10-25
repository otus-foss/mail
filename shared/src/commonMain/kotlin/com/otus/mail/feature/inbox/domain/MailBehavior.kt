package com.otus.mail.feature.inbox.domain

/**
 * How the inbox responds to the user. Swipes are named by layout direction: [startToEnd] is a
 * swipe right in left-to-right languages. [readLater] adds a Read later folder to each account, an
 * action that saves mail there, and its pill. [pills] are the inbox row's pills, in order.
 * [sidebar] arranges the sidebar. [actionBar] holds each place's bar, and [actionBarMode] picks
 * whether places use their own or the Everywhere bar. A [minimizedBar] shows only its primary,
 * as a round button. A null swipe does nothing. [websiteIcons] lets sender avatars load images
 * from outside the user's servers, such as sender websites and DuckDuckGo. [recentFrom] holds the
 * addresses the user last sent from, newest first, which compose offers first and a new message
 * from all accounts sends from. [signatureInReplies] adds the identity's signature to replies and
 * forwards too; new messages always get it. [undoSendSeconds] holds each sent message on the device
 * that long so Undo can stop it; 0 sends at once. [filters] arranges the filter panel. [list] sets
 * how the mail list's rows read. [remoteImages] decides when a message loads images from other
 * servers, and [allowedSenders] are the lowercase addresses whose images always load.
 * [showQuotes] shows a reply's quoted text even when earlier messages of its conversation are a
 * swipe away; off, the Read view leaves that text out. [zenRead] keeps the Read view to the
 * message, its control panel, and its bar. [screenSecurity] keeps Mail out of screenshots, screen
 * recordings, and recent apps, and [incognitoKeyboard] asks the keyboard not to learn from what the
 * user types, where [DevicePrivacy] allows them.
 */
data class MailBehavior(
    val startToEnd: MailAction? = MailAction.Read,
    val endToStart: MailAction? = MailAction.Trash,
    val markReadOnOpen: Boolean = true,
    val readLater: Boolean = false,
    val pills: List<InboxPill> = InboxPill.defaults(readLater),
    val sidebar: SidebarLayout = SidebarLayout(),
    val actionBar: Map<ActionBarPlace, ActionBarLayout> = ActionBarPlace.entries.associateWith { it.defaultLayout },
    val actionBarMode: ActionBarMode = ActionBarMode.Adaptive,
    val minimizedBar: Boolean = false,
    val websiteIcons: Boolean = true,
    val recentFrom: List<String> = emptyList(),
    val signatureInReplies: Boolean = true,
    val undoSendSeconds: Int = DEFAULT_UNDO_SEND_SECONDS,
    val inbox: InboxContents = InboxContents(),
    val filters: FilterLayout = FilterLayout(),
    val list: MailListLayout = MailListLayout(),
    val remoteImages: RemoteImages = RemoteImages.Known,
    val allowedSenders: List<String> = emptyList(),
    val showQuotes: Boolean = false,
    val zenRead: Boolean = false,
    val screenSecurity: Boolean = false,
    val incognitoKeyboard: Boolean = true,
) {
    /** [address] first among [recentFrom], keeping the [RECENT_FROM_LIMIT] newest. */
    fun sentFrom(address: String): MailBehavior =
        copy(
            recentFrom =
                (
                    listOf(address) +
                        recentFrom.filterNot {
                            it.equals(address, ignoreCase = true)
                        }
                ).take(RECENT_FROM_LIMIT),
        )

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

    /**
     * [from]'s bar as [to] would get it: the buttons [to] can offer, in order. When [to] cannot
     * offer [from]'s main button, [to] keeps its own.
     */
    fun copiedBar(
        from: ActionBarPlace,
        to: ActionBarPlace,
    ): ActionBarLayout {
        val source = savedLayout(from)
        val offered = { button: BarButton -> to.offers(button) && offers(button) }
        val primary = source.primary.takeIf(offered) ?: savedLayout(to).primary
        return ActionBarLayout(source.secondary.filter { offered(it) && it != primary }, primary)
    }

    private fun offers(button: BarButton): Boolean = readLater || button != MailAction.ReadLater

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

/** The Undo send delays settings offer, in seconds; 0 turns Undo send off. */
val UNDO_SEND_CHOICES =
    listOf(0, SHORT_UNDO_SEND_SECONDS, MEDIUM_UNDO_SEND_SECONDS, DEFAULT_UNDO_SEND_SECONDS, LONG_UNDO_SEND_SECONDS)

const val DEFAULT_UNDO_SEND_SECONDS = 15
private const val SHORT_UNDO_SEND_SECONDS = 5
private const val MEDIUM_UNDO_SEND_SECONDS = 10
private const val LONG_UNDO_SEND_SECONDS = 30

/** How many addresses [MailBehavior.recentFrom] keeps: the Send-from list's Recent section. */
const val RECENT_FROM_LIMIT = 2

/**
 * A list of addresses, such as [MailBehavior.recentFrom], as one stored line, and back. Addresses
 * cannot hold a newline.
 */
fun encodeAddresses(addresses: List<String>): String = addresses.joinToString("\n")

fun decodeAddresses(stored: String?): List<String> = stored?.lines()?.filter(String::isNotBlank).orEmpty()

/** How [MailBehavior.remoteImages] is stored. */
val RemoteImages.storageValue: String
    get() = name.lowercase()

/**
 * The image setting saved under its own key, [stored], else Never when a key it had before, in
 * [earlier], held Never. Each earlier key held its default for anyone who saved any setting, Known
 * and then Always, so an earlier value is kept only when it was a choice and differs from the
 * default now, which is Never alone. Null for a value [storageValue] cannot have made.
 */
fun decodeRemoteImages(
    stored: String?,
    vararg earlier: String?,
): RemoteImages? =
    RemoteImages.entries.firstOrNull { it.storageValue == stored }
        ?: RemoteImages.Never.takeIf { it.storageValue in earlier }

/** App-private storage for [MailBehavior]. Unknown or missing values read as the defaults. */
interface MailBehaviorStore {
    fun read(): MailBehavior

    fun write(behavior: MailBehavior)
}
