package com.otusfoss.mail.feature.inbox.presentation

/** Loading and connection context for the inbox, separate from the messages themselves. */
data class InboxStatus(
    val connected: Boolean = false,
    val loading: Boolean = false,
    val notice: String? = null,
    /** Whether older messages remain on the server, which scrolling to the end loads. */
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
)

/** [InboxFeedState]'s loading state, for an account list that is [connected]. */
fun InboxFeedState.status(connected: Boolean): InboxStatus =
    InboxStatus(connected, loading, notice, hasMore, loadingMore)
