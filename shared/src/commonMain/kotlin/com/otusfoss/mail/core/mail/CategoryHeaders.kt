package com.otusfoss.mail.core.mail

/**
 * The header fields the Inbox's categories read beside the list headers: `List-Post` (RFC 2369),
 * `Precedence`, `Auto-Submitted` (RFC 3834), `Feedback-ID`, and one header each of Mailchimp,
 * SendGrid, Amazon SES, Mailgun, and the Certified Senders Alliance, which bulk senders add, and
 * the headers GitHub, GitLab, Gitea, and Forgejo mark their notifications with.
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
        GITHUB_PROPERTY,
        GITLAB_PROPERTY,
        GITEA_PROPERTY,
        FORGEJO_PROPERTY,
    )

// RFC 8621, section 4.1.2.3 forbids asText for RFC 2369 headers, and servers reject the whole
// Email/get with invalidArguments. asRaw is allowed for every header and still names the address.
const val LIST_POST_PROPERTY = "header:List-Post:asRaw"
const val PRECEDENCE_PROPERTY = "header:Precedence:asText"
const val AUTO_SUBMITTED_PROPERTY = "header:Auto-Submitted:asText"
const val FEEDBACK_ID_PROPERTY = "header:Feedback-ID:asText"
const val MAILCHIMP_PROPERTY = "header:X-MC-User:asText"
const val SENDGRID_PROPERTY = "header:X-SG-EID:asText"
const val AMAZON_SES_PROPERTY = "header:X-SES-Outgoing:asText"
const val MAILGUN_PROPERTY = "header:X-Mailgun-Sid:asText"
const val CSA_PROPERTY = "header:X-CSA-Complaints:asText"
const val GITHUB_PROPERTY = "header:X-GitHub-Reason:asText"
const val GITLAB_PROPERTY = "header:X-GitLab-Project:asText"
const val GITEA_PROPERTY = "header:X-Gitea-Reason:asText"
const val FORGEJO_PROPERTY = "header:X-Forgejo-Reason:asText"
