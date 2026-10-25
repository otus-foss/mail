package com.otus.mail.feature.contacts.domain

/** A person's [name] and one of their addresses, such as someone to write to or a message's sender. */
data class ContactAddress(
    val name: String,
    val address: String,
)
