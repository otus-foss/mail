package com.otus.mail.feature.contacts.presentation

// iOS has no public phone number formatter, so numbers read as they were typed.
internal actual fun formatPhone(number: String): String = number
