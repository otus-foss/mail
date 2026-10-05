package com.otus.mail.feature.inbox.presentation

/** Loading and connection context for the inbox, separate from the messages themselves. */
data class InboxStatus(
    val connected: Boolean = false,
    val loading: Boolean = false,
    val notice: String? = null,
)
