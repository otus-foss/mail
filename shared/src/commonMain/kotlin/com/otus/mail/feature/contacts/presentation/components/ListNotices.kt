package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_call_merge
import com.otus.designsystem.resources.symbol_cloud_off
import com.otus.designsystem.resources.symbol_group
import com.otus.designsystem.resources.symbol_lock
import com.otus.designsystem.shared.feedback.components.InfoStrip
import com.otus.designsystem.shared.feedback.components.StripAction
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.PossibleDuplicate

/**
 * The strips above the contact list: a [readOnly] book someone shares, changes [waiting] to sync,
 * and the first possible [duplicate] out of [duplicates].
 */
internal class ListNotices(
    val readOnly: ContactBook?,
    val waiting: Int,
    val duplicate: PossibleDuplicate?,
    val duplicates: Int,
) {
    /** How many strips the list shows, so the letter positions can skip them. */
    val count: Int get() = listOfNotNull(readOnly, waiting.takeIf { it > 0 }, duplicate).size
}

/** Adds a strip for each notice; the duplicate strip's Review button calls [onReviewDuplicate]. */
internal fun LazyListScope.notices(
    notices: ListNotices,
    onReviewDuplicate: () -> Unit,
) {
    notices.readOnly?.let { book ->
        item("read-only") {
            InfoStrip(
                Res.drawable.symbol_lock,
                "${book.sharedBy ?: "Someone"} shares ${book.name} with you",
                "You can read it, not change it.",
                null,
                Modifier.padding(vertical = PageTokens.rowGap),
            )
        }
    }
    if (notices.waiting > 0) {
        item("waiting") {
            val changes = if (notices.waiting == 1) "1 change" else "${notices.waiting} changes"
            InfoStrip(
                Res.drawable.symbol_cloud_off,
                "$changes not synced yet",
                "They sync when you’re back online.",
                null,
                Modifier.padding(vertical = PageTokens.rowGap),
            )
        }
    }
    if (notices.duplicate != null) {
        item("duplicate") {
            val title =
                if (notices.duplicates == 1) {
                    "1 possible duplicate"
                } else {
                    "${notices.duplicates} possible duplicates"
                }
            InfoStrip(
                Res.drawable.symbol_group,
                title,
                null,
                StripAction("Review", Res.drawable.symbol_call_merge, onReviewDuplicate),
                Modifier.padding(vertical = PageTokens.rowGap),
            )
        }
    }
}
