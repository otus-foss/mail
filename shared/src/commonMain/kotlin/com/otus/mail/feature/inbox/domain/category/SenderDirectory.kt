package com.otus.mail.feature.inbox.domain.category

/**
 * The curated list of sender domains whose mail has a known category, such as social networks,
 * shops, payment providers, carriers, and CI services. It is data, kept in the app's
 * `files/sender_categories.txt`: a `version N` line, then one `category domain` pair per line,
 * where the category is a [MailCategory.storageValue]. `#` starts a comment. A domain also
 * covers its subdomains. Bump the version whenever an entry changes, so mail is classified again.
 */
class SenderDirectory(
    val version: Int,
    private val domains: Map<String, MailCategory>,
) {
    /** The listed domain [address] is from, or under, and its category; null when none is listed. */
    fun find(address: String): Pair<String, MailCategory>? {
        val domain =
            address
                .substringAfterLast('@', missingDelimiterValue = "")
                .trim()
                .trimEnd('.')
                .lowercase()
        if (domain.isEmpty()) return null
        return generateSequence(domain) { it.substringAfter('.', missingDelimiterValue = "").ifEmpty { null } }
            .firstNotNullOfOrNull { candidate -> domains[candidate]?.let { candidate to it } }
    }

    companion object {
        val Empty = SenderDirectory(version = 0, domains = emptyMap())

        /** Lines with an unknown category or no domain are left out, so an older app still reads a newer list. */
        fun parse(text: String): SenderDirectory {
            val lines =
                text
                    .lineSequence()
                    .map { it.substringBefore('#').trim() }
                    .filter { it.isNotEmpty() }
                    .map { it.split(WHITESPACE, limit = 2) }
                    .filter { it.size == 2 }
                    .toList()
            val version = lines.firstOrNull { it[0] == VERSION }?.get(1)?.trim()?.toIntOrNull() ?: 0
            val domains =
                lines
                    .filter { it[0] != VERSION }
                    .mapNotNull { (category, domain) ->
                        MailCategory.fromStorageValue(category)?.let { domain.trim().lowercase() to it }
                    }.toMap()
            return SenderDirectory(version, domains)
        }

        private const val VERSION = "version"
        private val WHITESPACE = Regex("""\s+""")
    }
}
