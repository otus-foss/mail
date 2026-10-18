package com.otus.mail.feature.inbox.domain

/** People to suggest while the user types an address, from contacts and earlier mail. */
fun interface AddressBook {
    /**
     * Up to a few people of [accountId] whose name or address starts with [query], contacts first.
     * Empty when the server cannot be read.
     */
    suspend fun suggestions(
        accountId: String,
        query: String,
    ): List<MailSender>
}
