package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.otus.designsystem.components.Collapsible
import com.otus.designsystem.components.ScopeChildRow
import com.otus.designsystem.components.ScopeOption
import com.otus.designsystem.components.ScopeRow
import com.otus.designsystem.elements.SectionCaption
import com.otus.mail.feature.inbox.domain.AccountAddresses
import com.otus.mail.feature.inbox.domain.DomainGroup
import com.otus.mail.feature.inbox.domain.MailScopeChoice
import com.otus.mail.feature.inbox.domain.domainGroups
import com.otus.mail.feature.inbox.presentation.addressCount
import com.otus.mail.feature.inbox.presentation.avatar
import com.otus.mail.feature.inbox.presentation.domainAvatar

/** The rows of a [MailScopeSheet]; [expanded] holds the keys of the open groups. */
internal class MailScopeRows(
    private val chosen: MailScopeChoice,
    private val count: (MailScopeChoice) -> String?,
    private val onChoose: (MailScopeChoice) -> Unit,
    private val onTogglePin: (MailScopeChoice.Address) -> Unit,
    private val expanded: List<String>,
    private val onExpandedChange: (key: String, open: Boolean) -> Unit,
) {
    /** One address as a top-level row, such as a pin or a search result. */
    @Composable
    fun Address(
        address: MailScopeChoice.Address,
        pinned: Boolean,
    ) {
        ScopeRow(
            addressOption(address, count(address), pinned),
            chosen = chosen == address,
            onClick = { onChoose(address) },
            onLongClick = { onTogglePin(address) },
        )
    }

    /** Each account, choosable as a whole, with its addresses nested under it. */
    @Composable
    fun Accounts(accounts: List<AccountAddresses>) {
        SectionCaption("Accounts")
        accounts.forEach { account ->
            val choice = MailScopeChoice.Account(account.accountId)
            Group(
                accountKey(account.accountId),
                ScopeOption(account.name, listOf(account.avatar), addressCount(account.addresses.size), count(choice)),
                choice,
                account.addresses.map { MailScopeChoice.Address(account.accountId, it) },
            )
        }
    }

    /** One account's addresses by domain; a domain only opens and closes, since it is no scope of its own. */
    @Composable
    fun Domains(accounts: List<AccountAddresses>) {
        val groups = domainGroups(accounts)
        if (groups.isEmpty()) return
        SectionCaption("By domain")
        groups.forEach { group ->
            Group(
                domainKey(group.domain),
                ScopeOption(
                    group.domain,
                    listOf(domainAvatar(group.domain)),
                    addressCount(group.addresses.size),
                    domainCount(group),
                ),
                choice = null,
                group.addresses,
            )
        }
    }

    @Composable
    private fun Group(
        key: String,
        option: ScopeOption,
        choice: MailScopeChoice?,
        addresses: List<MailScopeChoice.Address>,
    ) {
        val open = key in expanded
        ScopeRow(
            option,
            chosen = choice != null && chosen == choice,
            onClick = { if (choice != null) onChoose(choice) else onExpandedChange(key, !open) },
            expanded = open,
            onExpandedChange = { onExpandedChange(key, it) },
        )
        Collapsible(open) {
            Column {
                addresses.forEach { address ->
                    ScopeChildRow(
                        addressOption(address, count(address), pinned = false),
                        chosen = chosen == address,
                        onClick = { onChoose(address) },
                        onLongClick = { onTogglePin(address) },
                    )
                }
            }
        }
    }

    /** The domain's addresses' counts added up; null when none of them has one. */
    private fun domainCount(group: DomainGroup): String? =
        group.addresses
            .mapNotNull { count(it)?.toLongOrNull() }
            .takeIf { it.isNotEmpty() }
            ?.sum()
            ?.toString()
}

/** The key of the group that holds [chosen], so the sheet opens with it expanded. */
internal fun groupKey(
    chosen: MailScopeChoice,
    accounts: List<AccountAddresses>,
): String? {
    val accountId = chosen.accountId ?: return null
    return when {
        accounts.size > 1 -> accountKey(accountId)
        chosen is MailScopeChoice.Address -> domainKey(chosen.address.substringAfterLast('@').lowercase())
        else -> null
    }
}

private fun accountKey(accountId: String) = "account:$accountId"

private fun domainKey(domain: String) = "domain:$domain"
