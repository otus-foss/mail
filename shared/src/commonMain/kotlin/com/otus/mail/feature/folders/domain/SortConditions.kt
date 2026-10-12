package com.otus.mail.feature.folders.domain

/** Which part of a message a [SortCondition] matches mail by. */
enum class SortBy { Sender, Domain, Recipient, Subject }

/** A rule condition that matches mail like one message, by its [kind]. */
data class SortCondition(
    val kind: SortBy,
    val condition: RuleCondition,
)

/**
 * Conditions for sorting mail like one message, narrowest first: its [sender], everyone at the
 * sender's domain, the [recipient] address it was sent to, and a word from its [subject]. Parts
 * the message lacks are left out.
 */
fun sortConditions(
    sender: String,
    recipient: String,
    subject: String,
): List<SortCondition> =
    buildList {
        val address = sender.trim()
        if (address.isNotEmpty()) {
            add(
                SortCondition(SortBy.Sender, RuleCondition(RuleField.From, RuleOperator.Is, address)),
            )
        }
        address.substringAfterLast('@', "").takeIf { it.isNotEmpty() }?.let { domain ->
            add(SortCondition(SortBy.Domain, RuleCondition(RuleField.From, RuleOperator.EndsWith, "@$domain")))
        }
        recipient.trim().takeIf { it.isNotEmpty() }?.let {
            add(SortCondition(SortBy.Recipient, RuleCondition(RuleField.Recipient, RuleOperator.Is, it)))
        }
        subjectKeyword(subject)?.let {
            add(SortCondition(SortBy.Subject, RuleCondition(RuleField.Subject, RuleOperator.Contains, it)))
        }
    }

/**
 * The first word of [subject] with at least [MIN_KEYWORD_LENGTH] letters that is not a common
 * word, without the punctuation around it. Short words such as "Re" and "Fwd" are skipped by
 * length. Null when no word qualifies.
 */
fun subjectKeyword(subject: String): String? =
    subject
        .split(WHITESPACE)
        .map { word -> word.trim { !it.isLetter() } }
        .firstOrNull { word ->
            word.length >= MIN_KEYWORD_LENGTH && word.all { it.isLetter() } && word.lowercase() !in COMMON_WORDS
        }

private const val MIN_KEYWORD_LENGTH = 4
private val WHITESPACE = Regex("\\s+")

// English only for now: the words that start most subjects without saying what the mail is about.
private val COMMON_WORDS =
    (
        "about after again been before could from have here into just more only please should some than " +
            "that their them then there these they this were what when where which will with would your yours"
    ).split(' ').toSet()
