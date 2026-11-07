package com.otus.mail.feature.read.presentation.components

/**
 * How a page shows the text it quotes: left out with [hide], else folded, with a quoted person's
 * pill running what [showPerson] gives for their address, if anything.
 */
internal class PageQuotes(
    val hide: Boolean = false,
    val showPerson: (address: String) -> (() -> Unit)? = { null },
)
