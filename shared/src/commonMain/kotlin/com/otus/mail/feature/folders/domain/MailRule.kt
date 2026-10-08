package com.otus.mail.feature.folders.domain

/**
 * A rule that moves arriving mail into a folder. Rules run on the mail server in order when mail
 * is delivered; the first rule that matches moves the message and later rules are skipped.
 */
data class MailRule(
    val id: String,
    val name: String,
    val conditions: List<RuleCondition>,
    /** The target folder's server id. A deleted folder makes the server deliver to the inbox. */
    val folderId: String,
    /** True: every condition must match. False: any one condition is enough. */
    val matchAll: Boolean = true,
    val markRead: Boolean = false,
    /** A paused rule is kept but does not run. */
    val enabled: Boolean = true,
)

data class RuleCondition(
    val field: RuleField,
    val operator: RuleOperator,
    val value: String,
)

/**
 * The part of a message a condition tests. Address fields compare the address for "is", "starts
 * with", and "ends with", and the whole header, display names included, for the other operators.
 */
enum class RuleField {
    From,
    To,
    Cc,

    /** To or Cc. Blind copies leave no header, so they cannot be told apart reliably. */
    Recipient,
    Subject,
    Body,
    AttachmentType,
    AttachmentName,
    ;

    val isAddress: Boolean
        get() = this in setOf(From, To, Cc, Recipient)

    /** Body text is searched, not compared whole. */
    val operators: List<RuleOperator>
        get() =
            if (this == Body) {
                listOf(RuleOperator.Contains, RuleOperator.DoesNotContain, RuleOperator.MatchesRegex)
            } else {
                RuleOperator.entries
            }
}

/** Comparisons ignore letter case, as the server's default comparator does for ASCII. */
enum class RuleOperator {
    Contains,
    DoesNotContain,
    Is,
    IsNot,
    StartsWith,
    EndsWith,
    MatchesRegex,
    ;

    val negated: Boolean
        get() = this == DoesNotContain || this == IsNot
}

/** What the account's server can run, from its advertised Sieve extensions (RFC 9661). */
data class RuleSupport(
    val extensions: Set<String>,
    /** The largest script the server stores, in octets; null when unlimited. */
    val maxScriptBytes: Long? = null,
) {
    /** Filing by folder id (RFC 9042) keeps rules working after a folder is renamed or moved. */
    val canMoveToFolders: Boolean
        get() = "fileinto" in extensions && "mailboxid" in extensions

    val canMarkRead: Boolean
        get() = "imap4flags" in extensions

    /** Another script can keep running after the rules (RFC 6609). */
    val canKeepOtherScript: Boolean
        get() = "include" in extensions

    fun supports(field: RuleField): Boolean =
        when (field) {
            RuleField.Body -> "body" in extensions
            RuleField.AttachmentType, RuleField.AttachmentName -> "mime" in extensions
            else -> true
        }

    fun supports(operator: RuleOperator): Boolean = operator != RuleOperator.MatchesRegex || "regex" in extensions
}
