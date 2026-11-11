package com.otusfoss.mail.feature.folders.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.presentation.EditedList
import com.otusfoss.mail.core.messages.presentation.FutureSorting
import com.otusfoss.mail.core.messages.presentation.MoveTarget
import com.otusfoss.mail.core.messages.presentation.OlderMail
import com.otusfoss.mail.core.messages.presentation.SortChoice
import com.otusfoss.mail.core.messages.presentation.SortedMove
import com.otusfoss.mail.feature.folders.domain.FolderRule
import com.otusfoss.mail.feature.folders.domain.FolderRuleRepository
import com.otusfoss.mail.feature.folders.domain.SortBy
import com.otusfoss.mail.feature.folders.domain.SortCondition
import com.otusfoss.mail.feature.folders.domain.sortConditions
import com.otusfoss.mail.feature.folders.domain.withAdded
import com.otusfoss.mail.feature.folders.domain.withRemoved
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Rules that sort future mail like a moved message into the same folder. Once such a rule is saved,
 * [sorted] offers to undo the move or to move older Inbox and Archive mail the rule matches too.
 */
internal class MessageSorting(
    private val rules: FolderRulesState,
    private val repository: FolderRuleRepository,
    private val folders: FoldersState,
    private val filing: MessageFiling,
    private val scope: CoroutineScope,
) {
    var sorted by mutableStateOf<SortedMove?>(null)
        private set

    fun dismiss() {
        sorted = null
    }

    /**
     * Rules that sort future mail like [message], where its account's server runs rules. A rule
     * that is not saved is reported in [feed].
     */
    fun futureSorting(
        message: MailMessage,
        feed: EditedList,
    ): FutureSorting? {
        val account = rules.account(message.location.accountId)
        if (account?.support == null) return null
        val recipient =
            message.to
                .firstOrNull()
                ?.address
                .orEmpty()
        return sortConditions(message.sender.address, recipient, message.subject)
            .map { sort ->
                val person = message.personFor(sort.kind)
                SortChoice(sort.phrase, sort.keyword, sort.shown, sort.reach, person) { target, moved ->
                    addRule(account.accountId, sort, person, target, moved, feed)
                }
            }.takeIf { it.isNotEmpty() }
            ?.let { FutureSorting(account.accountName, it) }
    }

    private fun addRule(
        accountId: String,
        sort: SortCondition,
        person: MailSender?,
        target: MoveTarget,
        moved: List<MailMessage>,
        feed: EditedList,
    ) {
        val rule = FolderRule(sort.condition)
        rules.submit(
            accountId,
            onSaved = { sorted = sortedMove(accountId, sort.catches, person, rule, target, moved, feed) },
            onFailed = { feed.report("Couldn't add the rule. $it") },
        ) { it.withAdded(target.folderId, rule) }
    }

    private fun sortedMove(
        accountId: String,
        catches: String,
        person: MailSender?,
        rule: FolderRule,
        target: MoveTarget,
        moved: List<MailMessage>,
        feed: EditedList,
    ) = SortedMove(
        target.name,
        catches,
        person,
        undo = {
            moved.forEach { message -> scope.launch { filing.moveBack(feed, message, target) } }
            rules.submit(accountId, onFailed = { feed.report("Couldn't delete the rule. $it") }) {
                it.withRemoved(target.folderId, it[target.folderId].orEmpty().lastIndexOf(rule))
            }
        },
        older = {
            // Inbox and Archive mail the rule matches; the moved messages left the inbox first.
            repository.existingCount(accountId, target.folderId, rule)?.let { found ->
                OlderMail(found) {
                    scope.launch {
                        if (repository.moveExisting(accountId, target.folderId, rule) == null) {
                            feed.report("Couldn't move the older messages to ${target.label}. Try again.")
                        }
                        feed.refresh()
                        folders.refresh()
                    }
                }
            }
        },
    )
}

/**
 * Who a condition by [kind] names: the sender, the company of a sender's domain, or the recipient,
 * who is usually the account itself and then gets its avatar. Null for a subject.
 */
private fun MailMessage.personFor(kind: SortBy): MailSender? =
    when (kind) {
        SortBy.Sender -> sender

        SortBy.Recipient -> to.firstOrNull()?.withAccountAvatar(location)

        // The company the domain names, such as "acme" for "@acme.example".
        SortBy.Domain -> sender.address.substringAfterLast('@').let { MailSender(it.substringBefore('.'), it) }

        SortBy.Subject -> null
    }

private fun MailSender.withAccountAvatar(location: MailLocation): MailSender =
    if (avatar == null && address.equals(location.accountAddress, ignoreCase = true)) {
        copy(avatar = location.accountAvatar)
    } else {
        this
    }
