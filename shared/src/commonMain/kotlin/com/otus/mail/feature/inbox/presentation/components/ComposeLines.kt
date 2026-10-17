package com.otus.mail.feature.inbox.presentation.components

import com.otus.mail.feature.inbox.presentation.RecipientField

/** Which copy lines a draft shows, and what changes its scheduled time. */
internal class ComposeLines(
    val shown: Set<RecipientField>,
    val onChangeSchedule: () -> Unit,
)
