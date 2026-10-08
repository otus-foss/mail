package com.otus.mail.app

/** Moves between pages; [openSidebar] opens navigation from a top-level page. */
internal class MailNavigation(
    val go: (MailPage) -> Unit,
    val openSidebar: () -> Unit,
)
