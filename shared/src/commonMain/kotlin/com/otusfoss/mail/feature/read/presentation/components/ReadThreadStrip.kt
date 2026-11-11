package com.otusfoss.mail.feature.read.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.otusfoss.core.designsystem.mail.folders.components.FolderHint
import com.otusfoss.core.designsystem.mail.read.components.PositionPage
import com.otusfoss.core.designsystem.mail.read.components.TurningPositionStrip
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_flag
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.core.designsystem.shared.folders.elements.FolderStack
import com.otusfoss.core.designsystem.shared.people.components.NamedAvatar
import com.otusfoss.core.designsystem.shared.rows.elements.FirstFit
import com.otusfoss.core.designsystem.theme.colors.OtusColors
import com.otusfoss.core.designsystem.theme.tokens.content.ReadTokens
import com.otusfoss.core.designsystem.theme.tokens.rows.FolderTokens
import com.otusfoss.core.designsystem.theme.tokens.rows.StripTokens
import com.otusfoss.core.designsystem.theme.typography.panelTextStyle
import com.otusfoss.mail.core.messages.domain.ShownFolders
import com.otusfoss.mail.core.messages.domain.shownName
import com.otusfoss.mail.core.messages.presentation.components.chipAvatar

/**
 * The thread strip, newest first: "Latest" on the newest page, else the sender, with the position
 * and date of each page that [pageAt] gives, [current] on screen of [count]. It keeps still while
 * [slide] turns the pages: the dot of the page a drag heads to swells, and the strip shows that
 * page once letting go would turn to it. A drag on the strip turns the pages too.
 * Its end names the conversation's other [folders], and a flag when it is [flagged] and the list
 * gave no folder context. Screen readers hear the sender, the position, the date, and the folders,
 * such as "Grace Hopper, message 1 of 6, 12 Oct, also in Invoices".
 */
@Composable
internal fun ReadThreadStrip(
    pageAt: (Int) -> ThreadPosition,
    current: Int,
    count: Int,
    folders: ShownFolders,
    flagged: Boolean,
    onClick: () -> Unit,
    slide: StripGestures,
) {
    val flag = flagged && !folders.also
    TurningPositionStrip(
        count,
        current,
        slide.turn(),
        { index ->
            val position = pageAt(index)
            val page = position.page
            val sender = page.sender.shownName
            PositionPage(
                NamedAvatar(if (position.current == 0) "Latest" else sender, page.sender.chipAvatar),
                "${position.current + 1}/${position.count} · ${position.date}",
                spokenPosition(sender, position, folders, flag),
            )
        },
        onClick,
        slide.swipe,
    ) { FolderSlot(folders, flag) }
}

private fun spokenPosition(
    sender: String,
    position: ThreadPosition,
    folders: ShownFolders,
    flagged: Boolean,
): String =
    buildList {
        add("$sender, message ${position.current + 1} of ${position.count}, ${position.date}")
        if (folders.folders.isNotEmpty()) {
            val names = folders.folders.joinToString { it.name }
            add(if (folders.also) "also in $names" else "in $names")
        }
        if (flagged) add("flagged")
    }.joinToString()

/**
 * The short folder hint at the strip's end: "also", then one folder by name or stacked icons and a
 * count. Where that does not fit beside the position it drops "also", then a count's words; one
 * folder's name shortens instead.
 */
@Composable
private fun FolderSlot(
    folders: ShownFolders,
    flag: Boolean,
) {
    val shown = folders.folders
    if (shown.isEmpty() && !flag) return
    // The strip has little room, so a folder shows by its own name; the sheet shows full paths.
    val names = shown.map { folderName(it, listOf(it.name)) }
    val also = folders.also && shown.isNotEmpty()
    val stack = @Composable { if (shown.isNotEmpty()) FolderStack(names.map { if (it.system) null else it.color }) }
    val label =
        @Composable {
            if (shown.size == 1) {
                FolderHint(names, fontSize = ReadTokens.summarySize)
            } else {
                stack()
                if (shown.size > 1) SlotText("${shown.size} folders", OtusColors.text1)
            }
        }
    val full =
        @Composable {
            if (also) SlotText("also")
            label()
        }
    val options: List<@Composable () -> Unit> =
        if (shown.size > 1) {
            listOf({ SlotRow(flag, full) }, { SlotRow(flag, label) }, { SlotRow(flag, stack) })
        } else {
            listOf({ SlotRow(flag, full) }, { SlotRow(flag, label) })
        }
    FirstFit(options)
}

/** One version of the folder slot, after the flag when the conversation is [flagged]. */
@Composable
private fun SlotRow(
    flagged: Boolean,
    content: @Composable () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(StripTokens.glyphGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (flagged) {
            CompositionLocalProvider(LocalContentColor provides OtusColors.text2) {
                Symbol(Res.drawable.symbol_flag, size = FolderTokens.glyphSize)
            }
        }
        content()
    }
}

@Composable
private fun SlotText(
    text: String,
    color: Color = OtusColors.text3,
) {
    Text(
        text,
        style = panelTextStyle(),
        color = color,
        fontSize = ReadTokens.summarySize,
        maxLines = 1,
        softWrap = false,
    )
}
