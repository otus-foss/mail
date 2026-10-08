package com.otus.mail.designsystem.components

/** One button in an [ActionDialog]. Choosing it also closes the dialog. */
class DialogAction(
    val label: String,
    val onClick: () -> Unit,
    val primary: Boolean = false,
)
