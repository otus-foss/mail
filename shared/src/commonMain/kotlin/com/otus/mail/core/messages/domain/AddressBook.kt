package com.otus.mail.core.messages.domain

/** People to suggest while the user types an address, from contacts and earlier mail. */
fun interface AddressBook {
    /**
     * Up to a few people of [accountId] matching [query], names before addresses.
     * Empty when the server cannot be read.
     */
    suspend fun suggestions(
        accountId: String,
        query: String,
    ): List<MailSender>
}
