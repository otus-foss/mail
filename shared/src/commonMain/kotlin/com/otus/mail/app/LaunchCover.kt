package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.otus.designsystem.mail.launch.components.MailLaunch
import com.otus.designsystem.theme.brand.AppIconStyle
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first

/**
 * Covers the app with the launch animation, once per launch, until the inbox shows mail: the
 * device's copy, which comes before the server answers, or the first load's result. When Mail
 * opens on another page, not the [inbox], the inbox does not load, so the animation plays once.
 */
@Composable
internal fun LaunchCover(
    feed: InboxFeedState,
    style: AppIconStyle,
    inbox: Boolean,
) {
    var launching by rememberSaveable { mutableStateOf(true) }
    var inboxLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(feed) {
        snapshotFlow { feed.loading to feed.messages.isNotEmpty() }
            .dropWhile { (loading, shown) -> !loading && !shown }
            .first { (loading, shown) -> shown || !loading }
        inboxLoaded = true
    }
    if (launching) MailLaunch(style, loading = inbox && !inboxLoaded, onFinished = { launching = false })
}
