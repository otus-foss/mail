package com.otusfoss.mail.core.settings.data

import com.otusfoss.core.storage.KeyValueEditor
import com.otusfoss.core.storage.KeyValueStore
import com.otusfoss.core.storage.flag
import com.otusfoss.mail.core.categories.domain.InboxCategories
import com.otusfoss.mail.core.categories.domain.MailCategory
import com.otusfoss.mail.core.storage.TextCodec

internal fun KeyValueStore.inboxCategories(): InboxCategories {
    val defaults = InboxCategories()
    return InboxCategories(
        enabled = flag(CATEGORIES, defaults.enabled),
        hidden = string(HIDDEN_CATEGORIES)?.let(hiddenCategoryLines.decode) ?: defaults.hidden,
        senders = string(SENDER_CATEGORIES)?.let(senderCategoryLines.decode) ?: defaults.senders,
    )
}

internal fun KeyValueEditor.put(categories: InboxCategories) {
    put(CATEGORIES, categories.enabled)
    put(HIDDEN_CATEGORIES, hiddenCategoryLines.encode(categories.hidden))
    put(SENDER_CATEGORIES, senderCategoryLines.encode(categories.senders))
}

/** One category per line; a category this version does not know is left out. */
private val hiddenCategoryLines =
    TextCodec.Lines.map(
        { lines -> lines.mapNotNull(MailCategory::fromStorageValue).toSet() },
        { hidden -> hidden.map { it.storageValue } },
    )

/** An address, a tab, and a category per line; a line without a known category is left out. */
private val senderCategoryLines =
    TextCodec.Lines.map(
        { lines -> lines.mapNotNull(::senderCategory).toMap() },
        { senders -> senders.map { (address, category) -> "$address\t${category.storageValue}" } },
    )

private fun senderCategory(line: String): Pair<String, MailCategory>? {
    val address = line.substringBefore('\t').trim().lowercase()
    val category = MailCategory.fromStorageValue(line.substringAfter('\t', "")) ?: return null
    return (address to category).takeIf { address.isNotEmpty() }
}

private const val CATEGORIES = "inbox_categories"
private const val HIDDEN_CATEGORIES = "hidden_inbox_categories"
private const val SENDER_CATEGORIES = "sender_categories"
