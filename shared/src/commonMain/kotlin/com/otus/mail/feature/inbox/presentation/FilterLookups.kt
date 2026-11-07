package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.messages.domain.FoundPerson
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.PeopleField
import com.otus.mail.core.messages.domain.PeopleSearch
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.MailSource

/** What a filter panel asks the servers while it is open: the draft's count and people to pick. */
internal class FilterLookups(
    private val mail: MailSource,
    private val people: PeopleSearch,
    private val logins: () -> Set<String>?,
) {
    /** How many messages of [scope] [query] keeps; null when the accounts could not count them. */
    suspend fun count(
        scope: MailScope,
        query: MailQuery,
    ): Long? = mail.count(scope, query)

    /** People of the shown accounts' mail, or contacts, that [text] finds on [field]. */
    suspend fun findPeople(
        field: PeopleField,
        text: String,
    ): List<FoundPerson> = people.find(field, text, logins())
}
