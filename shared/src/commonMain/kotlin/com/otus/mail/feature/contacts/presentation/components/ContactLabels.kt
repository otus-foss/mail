package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** The custom labels in use, how many cards carry each, and removing one from all of them. */
internal class ContactLabels(
    val names: List<String>,
    val uses: (String) -> Int,
    val delete: (String) -> Unit,
)
