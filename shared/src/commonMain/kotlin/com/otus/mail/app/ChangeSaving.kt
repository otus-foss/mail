package com.otus.mail.app

import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.LocalChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Asks the server to [save] the [change] [feed] already shows. A saved change stays and the folder
 * counts reload; a refused one is rolled back, [onRefused] runs, and [feed] reports [failure].
 */
internal fun CoroutineScope.saveChange(
    change: LocalChange,
    feed: InboxFeedState,
    folders: FoldersState,
    failure: String,
    onRefused: () -> Unit = {},
    save: suspend () -> Boolean,
): Job =
    launch {
        if (save()) {
            change.confirm()
            folders.refresh()
        } else {
            change.rollBack()
            onRefused()
            feed.report(failure)
        }
    }
