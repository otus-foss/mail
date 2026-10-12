package com.otus.mail.app

import com.otus.mail.feature.folders.domain.sortConditions
import com.otus.mail.feature.folders.domain.withRule
import com.otus.mail.feature.folders.presentation.RuleDraft
import com.otus.mail.feature.folders.presentation.RulesState
import com.otus.mail.feature.folders.presentation.phrase
import com.otus.mail.feature.folders.presentation.reach
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.FutureSorting
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.SortChoice

/**
 * Rules that sort future mail like [message] into the folder it moves to, where its account's
 * server files mail by folder id. A rule that is not saved is reported in [feed].
 */
internal fun RulesState.futureSorting(
    message: MailMessage,
    feed: InboxFeedState,
): FutureSorting? {
    val account = account(message.location.accountId)
    if (account?.support?.canMoveToFolders != true) return null
    val choices =
        sortConditions(
            message.sender.address,
            message.to
                .firstOrNull()
                ?.address
                .orEmpty(),
            message.subject,
        ).map { sort ->
            SortChoice(sort.phrase, sort.reach) { target ->
                val rule =
                    RuleDraft(
                        account.accountId,
                        ruleId = null,
                        name = "Move mail that ${sort.phrase} to ${target.name}",
                        conditions = listOf(sort.condition),
                        folderId = target.folderId,
                    ).toRule()
                submit(
                    account.accountId,
                    onFailed = { feed.report("Couldn't add the rule. $it") },
                ) { it.withRule(rule) }
            }
        }
    return choices.takeIf { it.isNotEmpty() }?.let { FutureSorting(account.accountName, it) }
}
