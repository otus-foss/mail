package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.otus.designsystem.components.BannerAction
import com.otus.designsystem.components.ImageBanner
import com.otus.designsystem.components.SwipeToDismiss
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_hide_image
import com.otus.designsystem.resources.symbol_image
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageContent
import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.domain.RemoteImages
import com.otus.mail.feature.inbox.domain.inJunk
import com.otus.mail.feature.inbox.domain.orderedFolders
import com.otus.mail.feature.inbox.domain.showsRemoteImages
import com.otus.mail.feature.inbox.presentation.PageBody
import com.otus.mail.feature.inbox.presentation.ReadContext
import com.otus.mail.feature.inbox.presentation.ReadSheet
import com.otus.mail.feature.inbox.presentation.ReadState
import com.otus.mail.feature.inbox.presentation.read.fullLabel

/**
 * How the Read view's pages look: whether each one's remote images load, by the privacy setting,
 * the account's [known] people, still loading while null, and whether the page or the [row] is in
 * Spam; and every folder of the account by id, [folders], for the sender details.
 */
internal class ReadPageLook(
    private val read: ReadContext,
    private val state: ReadState,
    private val row: MailMessage,
    private val known: Set<String>?,
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
        return showsRemoteImages(setting, page.sender.address, people, read.privacy.allowed(), inSpam)
    }

    private fun hasRemote(body: PageBody): Boolean = (body as? PageBody.Loaded)?.content?.html?.remoteContent == true

    fun images(
        page: MailMessage,
        body: PageBody,
    ): PageImages {
        val load = allowed(page) == true || state.bodies.imagesShown(page)
        val onShow = { state.bodies.showImages(page) }.takeIf { !load && hasRemote(body) && allowed(page) != null }
        return PageImages(load, onShow)
    }

    /**
     * Whether [page]'s sender is not allowed to show remote images, so the ⋯ menu offers to always
     * show them, also after the user showed them this time.
     */
    fun blocked(
        page: MailMessage,
        body: PageBody,
    ): Boolean = hasRemote(body) && allowed(page) == false

    /**
     * The banner above [page]'s text: images blocked with Show, or shown this time with Hide and
     * Always for its sender; nothing once they are allowed.
     */
    @Composable
    fun Banner(
        page: MailMessage,
        body: PageBody,
    ) {
        if (!hasRemote(body) || allowed(page) != false) return
        val bodies = state.bodies
        if (!bodies.imagesShown(page)) {
            ImageBanner(
                Res.drawable.symbol_hide_image,
                "Images blocked for privacy",
                listOf(BannerAction("Show") { bodies.showImages(page) }),
            )
        } else {
            ShownBanner(page)
        }
    }

    /**
     * "Images shown" with Hide and Always, which swipes away like a toast. Once gone, its space
     * stays, so the text above which the page leaves room does not move and reload.
     */
    @Composable
    private fun ShownBanner(page: MailMessage) {
        val bodies = state.bodies
        var height by remember(page.id) { mutableIntStateOf(0) }
        if (bodies.bannerDismissed(page)) {
            Spacer(Modifier.height(with(LocalDensity.current) { height.toDp() }))
            return
        }
        SwipeToDismiss({ bodies.dismissBanner(page) }, Modifier.onSizeChanged { height = it.height }) {
            // Always shows this sender's images from now on, which the ⋯ menu names in full.
            ImageBanner(
                Res.drawable.symbol_image,
                "Images shown",
                listOf(
                    BannerAction("Hide") { bodies.hideImages(page) },
                    BannerAction("Always") { read.privacy.allowSender(page.sender.address) },
                ),
            )
        }
    }

    /**
     * Whether the page at [index] leaves its quoted text out: unless the user shows quotes, a page
     * with older pages in its conversation does, since a swipe shows them.
     */
    fun hidesQuote(index: Int): Boolean = !read.display.showQuotes() && index < state.pages.lastIndex

    /** The full record under the sender pill: every folder [page] is in, the one shown included. */
    @Composable
    fun Details(page: MailMessage) {
        val shown = orderedFolders(page.location.mailboxIds, folders)
        ReadSenderDetails(page, page.received.fullLabel(), shown, onFolders = { state.sheet = ReadSheet.Folders })
    }
}

/**
 * The conversation's pages side by side, newest first and leftmost; swiping left goes back in
 * time. Each page loads its body with [loadContent] as it or its neighbor shows, so swiping is
 * instant. The sender details show on the page on screen while [ReadState.details] is on; the pill
 * of a quoted person they list opens them.
 */
@Composable
internal fun ReadPages(
    state: ReadState,
    pager: PagerState,
    look: ReadPageLook,
    loadContent: suspend (MailMessage) -> MessageContent?,
    modifier: Modifier = Modifier,
    insets: PaddingValues = PaddingValues(),
) {
    // The pager's own drag is off: [rememberPageSwipe] in [modifier] turns the pages, more strictly.
    HorizontalPager(
        pager,
        modifier,
        beyondViewportPageCount = 1,
        userScrollEnabled = false,
        key = { state.pages.getOrNull(it)?.id ?: it },
    ) { index ->
        val page = state.pages.getOrNull(index) ?: return@HorizontalPager
        LaunchedEffect(page.id) { state.bodies.load(page, loadContent) }
        val body = state.bodies.body(page)
        val showPerson = { address: String -> { state.details = true }.takeIf { page.shows(address) } }
        val quotes = PageQuotes(look.hidesQuote(index), showPerson)
        ReadPageBody(page, body, look.images(page, body), Modifier.fillMaxSize(), quotes, insets) {
            look.Banner(page, body)
            if (state.details && index == pager.currentPage) look.Details(page)
        }
    }
}

/** Whether the sender details of this message show [address], as its sender or a recipient. */
private fun MailMessage.shows(address: String): Boolean =
    (listOf(sender) + to + cc).any { it.address.equals(address, ignoreCase = true) }

/**
 * Whether a page's remote images [load]; [onShow] loads them on a placeholder tap, null while
 * they load or the page has none.
 */
internal class PageImages(
    val load: Boolean,
    val onShow: (() -> Unit)?,
)
