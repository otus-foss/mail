package com.otus.mail.core.mail

/**
 * The header fields the Inbox's categories read beside the list headers: `List-Post` (RFC 2369),
 * `Precedence`, `Auto-Submitted` (RFC 3834), `Feedback-ID`, and one header each of Mailchimp,
 * SendGrid, Amazon SES, Mailgun, and the Certified Senders Alliance, which bulk senders add.
 */
val CATEGORY_HEADER_PROPERTIES =
    listOf(
        LIST_POST_PROPERTY,
        PRECEDENCE_PROPERTY,
        AUTO_SUBMITTED_PROPERTY,
        FEEDBACK_ID_PROPERTY,
        MAILCHIMP_PROPERTY,
        SENDGRID_PROPERTY,
        AMAZON_SES_PROPERTY,
        MAILGUN_PROPERTY,
        CSA_PROPERTY,
    )

const val LIST_POST_PROPERTY = "header:List-Post:asText"
const val PRECEDENCE_PROPERTY = "header:Precedence:asText"
const val AUTO_SUBMITTED_PROPERTY = "header:Auto-Submitted:asText"
const val FEEDBACK_ID_PROPERTY = "header:Feedback-ID:asText"
const val MAILCHIMP_PROPERTY = "header:X-MC-User:asText"
const val SENDGRID_PROPERTY = "header:X-SG-EID:asText"
const val AMAZON_SES_PROPERTY = "header:X-SES-Outgoing:asText"
const val MAILGUN_PROPERTY = "header:X-Mailgun-Sid:asText"
const val CSA_PROPERTY = "header:X-CSA-Complaints:asText"
