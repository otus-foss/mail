package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ImageBanner
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_hide_image
import com.otus.designsystem.resources.symbol_image
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageContent
import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.domain.folderHint
import com.otus.mail.feature.inbox.domain.showsRemoteImages
import com.otus.mail.feature.inbox.presentation.PageBody
import com.otus.mail.feature.inbox.presentation.ReadContext
import com.otus.mail.feature.inbox.presentation.ReadSheet
import com.otus.mail.feature.inbox.presentation.ReadState
import com.otus.mail.feature.inbox.presentation.read.fullLabel

/**
 * How the Read view's pages look: whether each one's remote images load, by the privacy setting,
 * the account's [known] people, still loading while null, and whether the page is in Spam; and
 * every folder of the account by id, [folders], for the sender details.
 */
internal class ReadPageLook(
    private val read: ReadContext,
    private val state: ReadState,
    private val known: Set<String>?,
    val folders: Map<String, MessageFolder>,
) {
    /** Whether [page]'s remote images may load without asking; null while that is not yet known. */
    private fun allowed(page: MailMessage): Boolean? {
        val people = known ?: return null
        val inSpam = page.location.mailboxIds.any { folders[it]?.role == JUNK_ROLE }
        val privacy = read.privacy
        return showsRemoteImages(privacy.setting(), page.sender.address, people, privacy.allowed(), inSpam)
    }

    private fun hasRemote(body: PageBody): Boolean = (body as? PageBody.Loaded)?.content?.html?.remoteContent == true

    fun images(
        page: MailMessage,
        body: PageBody,
    ): PageImages {
        val load = allowed(page) == true || state.imagesShown(page)
        val onShow = { state.showImages(page) }.takeIf { !load && hasRemote(body) && allowed(page) != null }
        return PageImages(load, onShow)
    }

    /** Whether [page]'s images wait for the user, which the banner and the ⋯ menu offer to change. */
    fun blocked(
        page: MailMessage,
        body: PageBody,
    ): Boolean = hasRemote(body) && allowed(page) == false && !state.imagesShown(page)

    /** The banner above [page]'s text: images blocked, or shown this time; nothing once they are allowed. */
    @Composable
    fun Banner(
        page: MailMessage,
        body: PageBody,
    ) {
        if (!hasRemote(body) || allowed(page) != false) return
        val sender =
            page.sender.name
                .ifBlank { page.sender.address }
                .substringBefore(' ')
        if (!state.imagesShown(page)) {
            ImageBanner(
                Res.drawable.symbol_hide_image,
                "Images blocked for privacy",
                "Show",
                { state.showImages(page) },
            )
        } else {
            ImageBanner(
                Res.drawable.symbol_image,
                "Images shown this time",
                "Always for $sender",
                { read.privacy.allowSender(page.sender.address) },
            )
        }
    }

    /** The full record under the sender pill: every folder [page] is in, the one shown included. */
    @Composable
    fun Details(page: MailMessage) {
        val shown = folderHint(page.location.mailboxIds, folders, cameFrom = null).folders
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
) {
    HorizontalPager(
        pager,
        modifier,
        beyondViewportPageCount = 1,
        key = { state.pages.getOrNull(it)?.id ?: it },
    ) { index ->
        val page = state.pages.getOrNull(index) ?: return@HorizontalPager
        LaunchedEffect(page.id) { state.load(page, loadContent) }
        val body = state.body(page)
        val showPerson = { address: String -> { state.details = true }.takeIf { page.shows(address) } }
        ReadPageBody(page, body, look.images(page, body), Modifier.fillMaxSize(), showPerson) {
            look.Banner(page, body)
            if (state.details && index == pager.currentPage) look.Details(page)
        }
    }
}

private const val JUNK_ROLE = "junk"

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
