package com.otus.mail

import com.otus.mail.core.messages.domain.OwnAddresses
import com.otus.mail.feature.read.presentation.ReadContext
import com.otus.mail.feature.read.presentation.ReadDisplay

/** This context for a user who owns [own]. */
internal fun ReadContext.ownedBy(own: OwnAddresses): ReadContext =
    ReadContext(pages, folders, privacy, files, services, effects, display, own, loadContent)

/** This context with replies showing their quotes, as the "Show quotes" setting does. */
internal fun ReadContext.showingQuotes(): ReadContext =
    ReadContext(
        pages,
        folders,
        privacy,
        files,
        services,
        effects,
        ReadDisplay({ true }, today = display.today),
        own,
        loadContent,
    )
