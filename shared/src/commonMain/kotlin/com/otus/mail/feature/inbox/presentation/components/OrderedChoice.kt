package com.otus.mail.feature.inbox.presentation.components

import org.jetbrains.compose.resources.DrawableResource

/** Something [OrderedChoiceSheet] offers, saved as [key] and listed under [group] by its [path]. */
internal data class OrderedChoice<out T>(
    val value: T,
    val key: String,
    val path: String,
    val icon: DrawableResource,
    val group: String,
)
