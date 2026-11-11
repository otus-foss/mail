package com.otusfoss.mail.core.settings.domain

/**
 * How the sidebar is arranged, and the [startPage] mailbox Mail opens on; while no account has it,
 * Mail opens on the Inbox and keeps the choice.
 */
data class SidebarBehavior(
    val layout: SidebarLayout = SidebarLayout(),
    val startPage: SidebarPin = SidebarPin.Inbox,
)
