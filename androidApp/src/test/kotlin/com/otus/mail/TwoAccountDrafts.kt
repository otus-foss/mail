package com.otus.mail

import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.DraftRepository
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.SendIdentity

/**
 * A Drafts folder in memory with two identities in Company A (`a1`), plus [moreCompanyAddresses],
 * and one in Rubylabs (`a2`).
 */
internal class TwoAccountDrafts(
    private val moreCompanyAddresses: List<String> = emptyList(),
) : DraftRepository {
    val saved = mutableListOf<Draft>()

    override suspend fun identities(accountId: String) =
        when (accountId) {
            "a1" -> companyIdentities()
            else -> listOf(identity("Alex Rivera", "otus@rubylabs.design", "a2"))
        }

    private fun companyIdentities() =
        listOf(
            identity("Alex Rivera", "this@companya.example", "a1"),
            identity("Company A Sales", "sales@companya.example", "a1"),
        ) + moreCompanyAddresses.map { identity("Alex Rivera", it, "a1") }

    override suspend fun save(draft: Draft): Result<String> {
        saved += draft
        return Result.success("d${saved.size}")
    }

    override suspend fun discard(
        accountId: String,
        emailId: String,
    ) = Result.success(Unit)

    private fun identity(
        name: String,
        address: String,
        account: String,
    ) = SendIdentity(address, MailSender(name, address, account))
}
