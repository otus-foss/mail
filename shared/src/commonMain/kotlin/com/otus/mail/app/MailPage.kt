package com.otus.mail.app

/** Every page, including subpages. Each belongs to one sidebar destination. */
internal enum class MailPage(
    val destination: MailDestination,
    val parent: MailPage?,
) {
    Inbox(MailDestination.Inbox, null),
    Settings(MailDestination.Settings, null),
    EmailAccounts(MailDestination.Settings, Settings),
    Appearance(MailDestination.Settings, Settings),
    ;

    companion object {
        fun of(destination: MailDestination): MailPage =
            entries.first {
                it.destination == destination &&
                    it.parent == null
            }
    }
}
