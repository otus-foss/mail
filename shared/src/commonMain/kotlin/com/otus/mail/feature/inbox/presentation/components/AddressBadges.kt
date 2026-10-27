package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.OwnAddresses
import com.otus.mail.feature.inbox.presentation.mixesAddresses

/**
 * Whether rows mark the address they came to. A filter can leave only rows of one address, so
 * while [narrowed] the list keeps the answer its unfiltered rows gave, and the badges stay put.
 */
@Composable
internal fun rememberMixedAddresses(
    messages: List<MailMessage>,
    own: OwnAddresses,
    narrowed: Boolean,
): Boolean {
    val mixed = remember(messages, own) { mixesAddresses(messages, own) }
    val unfiltered = remember { mutableStateOf(mixed) }
    if (!narrowed) SideEffect { unfiltered.value = mixed }
    return mixed || (narrowed && unfiltered.value)
}
