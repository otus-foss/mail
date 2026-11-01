package com.otus.mail.feature.inbox.domain

/** Someone the user deals with often: as they appear on mail, and whether they sent [unread] mail. */
data class FrequentSender(
    val person: MailSender,
    val unread: Boolean,
)

/**
 * The [limit] people the user deals with most in [messages], most first. Writing to someone counts
 * [WROTE_TO] for each To recipient, and mail from someone the user also wrote to counts [RECEIVED],
 * so senders the user never answers, such as newsletters, stay out while the user has written to
 * anyone; with no sent mail among [messages], every sender counts [RECEIVED] per email. The user's
 * own addresses, as [own] tells, never count.
 */
fun frequentSenders(
    messages: List<MailMessage>,
    own: OwnAddresses,
    limit: Int = PEOPLE_LIMIT,
): List<FrequentSender> {
    // Mail between the user's own logins is theirs on both ends, as in the combined inbox.
    val written = messages.filter { own.isMineAnywhere(it.sender.address) }
    val wroteTo = written.flatMap { message -> message.to.filterNot { own.isMineAnywhere(it.address) } }
    val received = messages.filterNot { own.isMineAnywhere(it.sender.address) }
    val writtenKeys = wroteTo.map { it.key }.toSet()
    val writes = wroteTo.map { it.key to WROTE_TO }
    // Without sent mail loaded, such as an inbox of incoming mail only, every sender counts.
    val replies =
        received
            .filter { writtenKeys.isEmpty() || it.sender.key in writtenKeys }
            .map { it.sender.key to RECEIVED }
    val scores =
        (writes + replies)
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, points) -> points.sum() }
    val faces = (received.map { it.sender } + wroteTo).associateBy { it.key }
    val unread = received.filter { it.attributes.unread }.map { it.sender.key }.toSet()
    return scores.entries
        .sortedByDescending { it.value }
        .take(limit)
        .mapNotNull { (key, _) -> faces[key]?.let { FrequentSender(it, key in unread) } }
}

private val MailSender.key: String
    get() = address.trim().lowercase()

private const val PEOPLE_LIMIT = 5
private const val WROTE_TO = 10
private const val RECEIVED = 1
