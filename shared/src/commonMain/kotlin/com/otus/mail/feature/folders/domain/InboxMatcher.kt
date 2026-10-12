package com.otus.mail.feature.folders.domain

/**
 * Inbox messages a rule matches, by [ids], among the newest [checked] in [inboxId]; [more] means
 * older mail was not checked.
 */
data class InboxMatches(
    val inboxId: String,
    val ids: List<String>,
    val checked: Int,
    val more: Boolean,
)

/** Finds inbox mail a new rule matches and files it, since the server's rules only see new mail. */
interface InboxMatcher {
    /** Inbox messages of [accountId] that [rule] matches; null when the server cannot be read. */
    suspend fun matches(
        accountId: String,
        rule: MailRule,
    ): InboxMatches?

    /** Moves [matches] to [rule]'s folder; how many moved, or null when the server cannot be reached. */
    suspend fun move(
        accountId: String,
        rule: MailRule,
        matches: InboxMatches,
    ): Int?
}
