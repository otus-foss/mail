package com.otus.mail.feature.folders.domain

/** Which part of a message a [SortCondition] matches mail by. */
enum class SortBy { Sender, Domain, Recipient, Subject }

/** A rule condition that matches mail like one message, by its [kind]. [value] is what it matches, as shown. */
data class SortCondition(
    val kind: SortBy,
    val value: String,
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
            add(SortCondition(SortBy.Sender, address, from(AddressMatch.Exact(address))))
        }
        val domain = address.substringAfterLast('@', "")
        if (domain.isNotEmpty()) add(SortCondition(SortBy.Domain, "@$domain", from(AddressMatch.Domain(domain))))
        val recipientAddress = recipient.trim()
        if (recipientAddress.isNotEmpty()) {
            val condition = RuleCondition.Address(AddressField.To, AddressMatch.Exact(recipientAddress))
            add(SortCondition(SortBy.Recipient, recipientAddress, condition))
        }
        val keyword = subjectKeyword(subject)
        if (keyword != null) {
            val condition = RuleCondition.Text(TextField.Subject, TextOperator.Contains, keyword)
            add(SortCondition(SortBy.Subject, keyword, condition))
        }
    }

private fun from(match: AddressMatch) = RuleCondition.Address(AddressField.From, match)

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
