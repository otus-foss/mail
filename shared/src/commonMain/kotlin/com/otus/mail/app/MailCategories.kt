package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.otus.mail.feature.inbox.data.CategoryRecords
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.category.InboxCategories
import com.otus.mail.feature.inbox.domain.category.MailCategory
import com.otus.mail.feature.inbox.presentation.CategoryTabs
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.label
import kotlinx.coroutines.flow.drop

/**
 * Inbox category sorting for the mail source: [records] classifies listed mail while [enabled]
 * says the user turned categories on. [follow] ties it to the saved setting.
 */
internal class MailCategorySorting {
    private var enabled: () -> Boolean = { false }

    val records = CategoryRecords(::senderCategories, { enabled() })

    /** Classifies while [behavior] has categories on. */
    fun follow(behavior: MailBehaviorState) {
        enabled = { behavior.behavior.categories.enabled }
    }
}

/**
 * Turning categories on sorts the mail already listed, which only a new list read does, so the
 * inbox loads again. Turning them off needs nothing: the pills go and rows keep their order.
 */
@Composable
internal fun CategoryEffects(
    states: MailStates,
    behavior: MailBehaviorState,
) {
    LaunchedEffect(states, behavior) {
        snapshotFlow { behavior.behavior.categories.enabled }
            .drop(1)
            .collect { on -> if (on) states.feed.refresh() }
    }
}

/**
 * The Inbox's category pills over [messages], or null while [categories] are off. The selected
 * pill outlives the page; a pill the user hides falls back to Primary.
 */
@Composable
internal fun rememberCategoryTabs(
    categories: InboxCategories,
    messages: List<MailMessage>,
): CategoryTabs? {
    var selected by rememberSaveable { mutableStateOf(MailCategory.Primary.storageValue) }
    val unread = remember(categories, messages) { categories.unreadCounts(messages) }
    if (!categories.enabled) return null
    val tab = MailCategory.fromStorageValue(selected)?.takeIf(categories::shows) ?: MailCategory.Primary
    return CategoryTabs(categories, tab, unread) { selected = it.storageValue }
}

/** What an empty category pill says. */
internal fun CategoryTabs.emptyTitle(): String? =
    selected.takeIf { it != MailCategory.Primary }?.let { "Nothing in ${it.label}" }
