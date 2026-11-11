package com.otusfoss.mail.feature.folders.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * One rule chip on a folder: a single condition, or an "All of" group when there are several.
 * A folder's rules are "any of": new mail that matches one of them is filed into the folder.
 */
data class FolderRule(
    val conditions: List<RuleCondition>,
) {
    constructor(condition: RuleCondition) : this(listOf(condition))

    val isGroup: Boolean
        get() = conditions.size > 1
}

/** What one condition tests. [negated] reads as "isn't from" or "doesn't contain". */
sealed interface RuleCondition {
    val negated: Boolean

    /** An address in From, To, or Cc. */
    data class Address(
        val field: AddressField,
        val match: AddressMatch,
        override val negated: Boolean = false,
    ) : RuleCondition

    /** Subject or body text. The body only supports [TextOperator.Contains]. */
    data class Text(
        val field: TextField,
        val operator: TextOperator,
        val value: String,
        override val negated: Boolean = false,
    ) : RuleCondition

    /** Any header by [name], such as `X-Project`. */
    data class Header(
        val name: String,
        val operator: HeaderOperator,
        val value: String,
        override val negated: Boolean = false,
    ) : RuleCondition

    /** Mail from one mailing list, by the id in its `List-Id` header (RFC 2919), such as `releases.github.example`. */
    data class MailingList(
        val listId: String,
        override val negated: Boolean = false,
    ) : RuleCondition

    /** Messages [over] or under a size in bytes. */
    data class Size(
        val over: Boolean,
        val bytes: Long,
    ) : RuleCondition {
        override val negated: Boolean
            get() = false
    }

    /**
     * Mail that arrives on [days] (every day when empty), within [range], or [outside] it. Without a
     * range, [outside] has no effect.
     */
    data class When(
        val days: Set<DayOfWeek>,
        val range: TimeRange? = null,
        val outside: Boolean = false,
    ) : RuleCondition {
        override val negated: Boolean
            get() = false
    }

    /** Mail the server scores as spam, from [minScore] on its 0 to 10 scale (RFC 5235). */
    data class Spam(
        val minScore: Int,
    ) : RuleCondition {
        override val negated: Boolean
            get() = false
    }
}

enum class AddressField { From, To, Cc }

/** How an address matches: one address, everyone at a domain, or the part before the @ at any domain. */
sealed interface AddressMatch {
    val value: String

    data class Exact(
        val address: String,
    ) : AddressMatch {
        override val value: String
            get() = address
    }

    data class Domain(
        val domain: String,
    ) : AddressMatch {
        override val value: String
            get() = domain
    }

    data class LocalPart(
        val local: String,
    ) : AddressMatch {
        override val value: String
            get() = local
    }
}

enum class TextField { Subject, Body }

/** [Matches] is a wildcard pattern: `*` stands for any text and `?` for one character. */
enum class TextOperator { Contains, StartsWith, Matches }

enum class HeaderOperator { Is, Contains }

/** A time of day range. When [start] is after [end], it runs over midnight. */
data class TimeRange(
    val start: LocalTime,
    val end: LocalTime,
)

/** Monday to Friday. */
val WEEKDAYS: Set<DayOfWeek> =
    setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
