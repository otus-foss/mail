package com.otusfoss.mail.feature.contacts.presentation.components

/** The custom labels in use, how many cards carry each, and removing one from all of them. */
internal class ContactLabels(
    val names: List<String>,
    val uses: (String) -> Int,
    val delete: (String) -> Unit,
)
