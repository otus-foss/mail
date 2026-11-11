package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otusfoss.core.designsystem.mail.folders.components.FolderColorNudge
import com.otusfoss.core.designsystem.theme.tokens.feedback.NudgeTokens
import com.otusfoss.mail.core.messages.presentation.ListNudge

/** The folder color nudge as the list's first item, scrolling with the rows. */
@Composable
internal fun ListNudgeCard(
    nudge: ListNudge,
    modifier: Modifier = Modifier,
) {
    FolderColorNudge(
        title = "${nudge.folderName} has no colour yet.",
        body = "Coloured folders are easier to spot next to each mail.",
        colors = nudge.colors,
        onPick = nudge.onPick,
        onDismiss = nudge.onDismiss,
        modifier =
            modifier.padding(
                start = NudgeTokens.listInset,
                end = NudgeTokens.listInset,
                bottom = NudgeTokens.listGap,
            ),
    )
}
