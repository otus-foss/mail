package com.otus.mail.feature.folders.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.login.LoginStore
import com.otus.mail.feature.folders.domain.FolderStorageException
import com.otus.mail.feature.folders.domain.InboxMatcher
import com.otus.mail.feature.folders.domain.InboxMatches
import com.otus.mail.feature.folders.domain.MailRule

/** Reads and moves inbox matches with the same [InboxRuleApplier] the rule editor uses. */
class JmapInboxMatcher(
    connections: JmapConnections,
    logins: LoginStore,
) : InboxMatcher {
    private val accounts = MailAccounts(connections, logins)

    override suspend fun matches(
        accountId: String,
        rule: MailRule,
    ): InboxMatches? = withApplier(accountId) { it.matches(rule) }

    override suspend fun move(
        accountId: String,
        rule: MailRule,
        matches: InboxMatches,
    ): Int? = withApplier(accountId) { it.move(matches, rule) }

    private suspend fun <T> withApplier(
        accountId: String,
        use: suspend (InboxRuleApplier) -> T,
    ): T? =
        try {
            accounts.open(accountId)?.let { use(InboxRuleApplier(it)) }
        } catch (expected: JmapException) {
            null
        } catch (expected: FolderStorageException) {
            null
        }
}
