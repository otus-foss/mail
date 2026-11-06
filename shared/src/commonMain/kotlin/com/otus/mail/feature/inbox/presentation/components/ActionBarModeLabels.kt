package com.otus.mail.feature.inbox.presentation.components

import com.otus.mail.feature.inbox.domain.ActionBarMode

/** The action bar mode as settings name it. */
internal val ActionBarMode.label: String
    get() =
        when (this) {
            ActionBarMode.Adaptive -> "Per screen"
            ActionBarMode.Fixed -> "Same everywhere"
        }

internal val ActionBarMode.description: String
    get() =
        when (this) {
            ActionBarMode.Adaptive -> "Each screen shows its own buttons"
            ActionBarMode.Fixed -> "Every screen shows the same buttons"
        }
