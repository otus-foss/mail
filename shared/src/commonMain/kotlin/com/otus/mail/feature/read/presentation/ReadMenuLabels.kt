package com.otus.mail.feature.read.presentation

/** The caption above a menu section: the sender's name, "Whole conversation", or none. */
fun ReadMenuPart.title(): String? =
    when (this) {
        ReadMenuPart.ThisMessage -> null
        is ReadMenuPart.Sender -> name
        ReadMenuPart.Conversation -> "Whole conversation"
    }
