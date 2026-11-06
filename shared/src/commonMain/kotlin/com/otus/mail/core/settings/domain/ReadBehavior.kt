package com.otus.mail.core.settings.domain

import com.otus.mail.core.messages.domain.RemoteImages

/**
 * How the Read view shows a message. [showQuotes] shows a reply's quoted text even when earlier
 * messages of its conversation are a swipe away; off, the Read view leaves that text out. [zen]
 * keeps the Read view to the message, its control panel, and its bar. [quickView] picks the
 * modules its control panel shows when pulled down. [remoteImages] decides when a message loads
 * images from other servers, and [allowedSenders] are the lowercase addresses whose images always
 * load.
 */
data class ReadBehavior(
    val markReadOnOpen: Boolean = true,
    val showQuotes: Boolean = false,
    val zen: Boolean = false,
    val quickView: List<ReadQuickModule> = ReadQuickModule.Defaults,
    val remoteImages: RemoteImages = RemoteImages.Known,
    val allowedSenders: List<String> = emptyList(),
)
