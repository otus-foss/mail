package com.otus.mail.app

import com.otus.mail.designsystem.resources.Res
import com.otus.mail.feature.inbox.domain.category.SenderDirectory
import org.jetbrains.compose.resources.ExperimentalResourceApi

// Data files Mail ships with, read once when first needed.

@OptIn(ExperimentalResourceApi::class)
internal suspend fun avatarSuffixRules(): String = Res.readBytes("files/public_suffix_list.dat").decodeToString()

/** The curated sender list Inbox categories start from; see docs/categories.md. */
@OptIn(ExperimentalResourceApi::class)
internal suspend fun senderCategories(): SenderDirectory =
    SenderDirectory.parse(Res.readBytes("files/sender_categories.txt").decodeToString())
