package com.otusfoss.mail.feature.read.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.otusfoss.core.designsystem.mail.read.components.ImageBanner
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_hide_image
import com.otusfoss.core.designsystem.resources.symbol_image
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.feedback.components.ToastMessage
import com.otusfoss.core.designsystem.shared.pages.elements.holdsSpace
import com.otusfoss.core.designsystem.shared.pages.elements.turnDim
import com.otusfoss.core.designsystem.shared.pages.elements.turnSeam
import com.otusfoss.core.designsystem.shared.rows.components.DetailLine
import com.otusfoss.core.designsystem.shared.swipe.components.SwipeToDismiss
import com.otusfoss.core.settings.presentation.components.LocalDateFormatter
import com.otusfoss.mail.core.mail.EmailAddressKey
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MessageContent
import com.otusfoss.mail.core.messages.domain.MessageFolder
import com.otusfoss.mail.core.messages.domain.RemoteImages
import com.otusfoss.mail.core.messages.domain.inJunk
import com.otusfoss.mail.core.messages.domain.isVerified
import com.otusfoss.mail.core.messages.domain.orderedFolders
import com.otusfoss.mail.core.messages.domain.showsRemoteImages
import com.otusfoss.mail.core.messages.presentation.fullLabel
import com.otusfoss.mail.feature.read.presentation.PageBody
import com.otusfoss.mail.feature.read.presentation.ReadContext
import com.otusfoss.mail.feature.read.presentation.ReadSheet
import com.otusfoss.mail.feature.read.presentation.ReadState
import kotlinx.coroutines.flow.Flow

/**
 * How the Read view's pages look: whether each one's remote images load, by the privacy setting,
 * the account's [known] people, still loading while null, and whether the page or the [row] is in
 * Spam; and every folder of the account by id, [folders], for the sender details.
 */
internal class ReadPageLook(
    private val read: ReadContext,
    private val state: ReadState,
    private val row: MailMessage,
    private val known: Set<EmailAddressKey>?,
    val folders: Map<String, MessageFolder>,
) {
    /**
     * Whether [page]'s remote images may load without asking; null while the known people that
     * decide it are loading. A folder the account does not list yet counts as Spam, so images wait
     * until it is known.
     */
    private fun allowed(page: MailMessage): Boolean? {
        val setting = read.privacy.setting()
        val people = known ?: if (setting == RemoteImages.Known) return null else emptySet()
        val boxes = page.location.mailboxIds + page.location.mailboxId + row.location.mailboxId - ""
        val inSpam = inJunk(boxes, folders) || boxes.any { it !in folders }
        return showsRemoteImages(
            setting,
            page.sender.address,
            page.sender.isVerified,
            people,
            read.privacy.allowed(),
            inSpam,
        )
    }

    private fun hasRemote(body: PageBody): Boolean = (body as? PageBody.Loaded)?.content?.html?.remoteContent == true

    fun images(
        page: MailMessage,
        body: PageBody,
    ): PageImages {
        val load = allowed(page) == true || state.bodies.imagesShown(page)
        val onPlaceholder = { askToShow(page) }.takeIf { !load && hasRemote(body) }
        return PageImages(load, onPlaceholder)
    }

    /**
     * Asks in a toast whether to show [page]'s images, since a placeholder sits inside the message,
     * whose own styles can stretch it unseen over the text. The app draws the toast above the page,
     * so its Show is the user's choice. Another tap replaces the toast instead of adding one.
     */
    private fun askToShow(page: MailMessage) =
        read.effects.toasts.show(
            ToastMessage("Images blocked", UiAction("Show") { state.bodies.showImages(page) }, group = ImagesPrompt),
        )

    /**
     * Whether [page]'s sender is not allowed to show remote images, so the ⋯ menu offers to always
     * show them, also after the user showed them this time. Not for a sender the server did not
     * confirm, since allowing that address would not load its images.
     */
    fun blocked(
        page: MailMessage,
        body: PageBody,
    ): Boolean = hasRemote(body) && allowed(page) == false && page.sender.isVerified

    /**
     * The banner above [page]'s text, below the sender details: images blocked with Show, or shown
     * this time with Hide, and Always for a confirmed sender; nothing once they are allowed. It
     * shows while the rule that may allow them is still loading too, when they are blocked all the
     * same, so the text below it does not move and reload once the rule answers. Zen mode hides it
     * but keeps its space, so the text does not move and reload; there a tap on an image's
     * placeholder asks in a toast, whose Show loads them.
     */
    @Composable
    fun Banner(
        page: MailMessage,
        body: PageBody,
    ) {
        if (!hasRemote(body) || allowed(page) == true) return
        val bodies = state.bodies
        Dismissible(page, Modifier.holdsSpace(read.display.zen())) {
            if (!bodies.imagesShown(page)) {
                ImageBanner(
                    Res.drawable.symbol_hide_image,
                    "Images blocked for privacy",
                    listOf(UiAction("Show") { bodies.showImages(page) }),
                )
            } else {
                // Always shows this sender's images from now on, which the ⋯ menu names in full.
                ImageBanner(
                    Res.drawable.symbol_image,
                    "Images shown",
                    listOfNotNull(
                        UiAction("Hide") { bodies.hideImages(page) },
                        UiAction("Always") { read.privacy.allowSender(page.sender.address) }
                            .takeIf { page.sender.isVerified },
                    ),
                )
            }
        }
    }

    /**
     * [banner], which swipes away like a toast, in either state; the images stay as they are, and
     * a tap on their placeholders still asks to show them. Once gone, its space stays, so the text the page leaves
     * room for does not move and reload.
     */
    @Composable
    private fun Dismissible(
        page: MailMessage,
        modifier: Modifier,
        banner: @Composable () -> Unit,
    ) {
        val bodies = state.bodies
        var height by remember(page.id) { mutableIntStateOf(0) }
        if (bodies.bannerDismissed(page)) {
            Spacer(Modifier.height(with(LocalDensity.current) { height.toDp() }))
            return
        }
        SwipeToDismiss({ bodies.dismissBanner(page) }, modifier.onSizeChanged { height = it.height }) { banner() }
    }

    /**
     * Whether the page at [index] leaves its quoted text out: unless the user shows quotes, a page
     * with older pages in its conversation does, since a swipe shows them. Until the pages load,
     * the row's count says whether there are older ones, so the page's HTML is not loaded twice.
     */
    fun hidesQuote(index: Int): Boolean = !read.display.showQuotes() && index < state.messageCount(row) - 1

    /** [page]'s full record, with every folder it is in, the one shown included. */
    @Composable
    fun detailLines(page: MailMessage): List<DetailLine> =
        messageDetailLines(
            page,
            LocalDateFormatter.current.fullLabel(page.received),
            orderedFolders(page.location.mailboxIds, folders),
            onFolders = { state.sheet = ReadSheet.Folders },
            onPerson = read.services.person?.let { { person -> state.sheet = ReadSheet.Person(person) } },
        )
}

/**
 * The conversation's pages side by side, newest first and leftmost; swiping left goes back in
 * time. Each page loads its body with [loadContent] as it or its neighbor shows, so swiping is
 * instant. The sender details show on the page on screen while [ReadState.details] is on; the pill
 * of a quoted person they list opens them. While a drag of [swipes] heads to another page, the page
 * that letting go lands on is the bright one, and a line marks the edge between the two.
 */
@Composable
internal fun ReadPages(
    state: ReadState,
    swipes: ReadSwipes,
    look: ReadPageLook,
    loadContent: (MailMessage) -> Flow<MessageContent?>,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
    insets: PaddingValues = PaddingValues(),
) {
    // The pager's own drag is off: [ReadSwipes.pages] in [modifier] turns the pages, more strictly.
    HorizontalPager(
        swipes.pager,
        modifier,
        beyondViewportPageCount = 1,
        userScrollEnabled = false,
        key = { state.pages.getOrNull(it)?.id ?: it },
    ) { index ->
        val page = state.pages.getOrNull(index) ?: return@HorizontalPager
        LaunchedEffect(page.id) { state.bodies.load(page, loadContent) }
        val body = state.bodies.body(page)
        val showPerson = { address: String -> onDetails.takeIf { page.shows(address) } }
        val quotes = PageQuotes(look.hidesQuote(index), showPerson)
        val turning = Modifier.fillMaxSize().turnSeam(index, swipes.turn).turnDim(index, swipes.turn)
        ReadPageBody(page, body, look.images(page, body), turning, quotes, insets) {
            look.Banner(page, body)
        }
    }
}

/** Whether the sender details of this message show [address], as its sender or a recipient. */
private fun MailMessage.shows(address: String): Boolean =
    (listOf(sender) + to + cc).any { it.address.equals(address, ignoreCase = true) }

/**
 * Whether a page's remote images [load]; [onPlaceholder] runs on a placeholder tap and asks to
 * load them, null while they load or the page has none. The placeholders show from the start,
 * also while the rule that may allow the images is still loading, so a page whose images stay
 * blocked loads once.
 */
internal class PageImages(
    val load: Boolean,
    val onPlaceholder: (() -> Unit)?,
)

/** The group of the toast that asks to show images, so repeated taps keep one toast. */
private object ImagesPrompt
