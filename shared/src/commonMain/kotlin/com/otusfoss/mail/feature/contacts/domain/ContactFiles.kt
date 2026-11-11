package com.otusfoss.mail.feature.contacts.domain

import com.otusfoss.core.jscontact.Card

/** vCard files the user picks to import and saves when exporting. */
interface ContactFiles {
    /** The cards in a file the user picks; null when they pick none. */
    suspend fun pick(): ContactFile?

    /** Saves [cards] as a vCard file named [name] in the device's downloads; false when that failed. */
    suspend fun save(
        name: String,
        cards: List<Card>,
    ): Boolean
}

/**
 * A vCard file read for import: its [name], the [cards] it holds, and the ones it could not read,
 * each with its position in the file and why.
 */
data class ContactFile(
    val name: String,
    val cards: List<Card>,
    val failures: List<Pair<Int, String>>,
)
