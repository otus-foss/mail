package com.otusfoss.mail.core.codes.domain

/** The code [findSignInCode] finds in a message with [subject] and [texts], as typed; null for none. */
internal fun codeIn(
    subject: String,
    vararg texts: String,
    fromList: Boolean = false,
): String? = findSignInCode(CodeMail(subject, texts.toList(), fromList))?.value
