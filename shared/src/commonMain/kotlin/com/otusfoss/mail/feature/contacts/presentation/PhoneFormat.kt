package com.otusfoss.mail.feature.contacts.presentation

/**
 * [number] spaced the way its country writes it, such as "+41 78 736 79 69", keeping the style it
 * was typed in: international with a country code, else national for the device's country. The
 * stored number never changes; this is only how it reads. Unknown numbers come back as typed.
 */
internal expect fun formatPhone(number: String): String
