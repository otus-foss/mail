package com.otus.mail.feature.inbox.presentation.read

/**
 * Who wrote a quoted message and when, read from the line above the quote. [date] is as written,
 * or the day and month, such as "10 Oct", when it reads as a date; [fullDate] adds the year and the
 * time, such as "10 Oct 2026, 17:50". [address] is null when the line names none.
 */
data class QuoteHeader(
    val name: String,
    val address: String?,
    val date: String?,
    val fullDate: String? = date,
)

/**
 * The header in an attribution line, "On <date>, <name> wrote:" as Apple Mail and Thunderbird
 * write it, "On <date> at <time> <name> <address> wrote:" as Gmail does, or German
 * "Am <date> schrieb <name>:". Null for any other line, and for one longer than
 * [MAX_ATTRIBUTION_LENGTH], which is message text rather than a header.
 */
internal fun attributionHeader(line: String): QuoteHeader? {
    val text = line.trim().replace(SPACES, " ")
    if (text.length > MAX_ATTRIBUTION_LENGTH) return null
    return ENGLISH.matchEntire(text)?.let { dateThenPerson(it.groupValues[1]) } ?: germanHeader(text)
}

private fun germanHeader(text: String): QuoteHeader? =
    GERMAN.matchEntire(text)?.let { match ->
        val date = match.groupValues[1].replace(GERMAN_TIME, "")
        person(match.groupValues[2], date)
    }

/**
 * The header in the fields Outlook writes above a quote, "From:" and "Sent:" or "Date:" lines,
 * also in German. Null without a From line.
 */
internal fun fieldsHeader(lines: List<String>): QuoteHeader? {
    val fields =
        lines
            .mapNotNull { FIELD.matchEntire(it.trim()) }
            .associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }
    val from = FROM_FIELDS.firstNotNullOfOrNull(fields::get) ?: return null
    return person(from, DATE_FIELDS.firstNotNullOfOrNull(fields::get))
}

/** Whether [line] is a field such as "From: Ada", the kind Outlook writes above a quote. */
internal fun isHeaderField(line: String): Boolean = FIELD.matches(line.trim())

private fun dateThenPerson(text: String): QuoteHeader {
    val address = ADDRESS.findAll(text).lastOrNull()
    val beforeAddress = address?.let { text.substring(0, it.range.first) } ?: text
    val time = TIME.findAll(beforeAddress).lastOrNull()
    val (date, name) =
        when {
            time != null -> beforeAddress.splitAt(time.range.last + 1)
            beforeAddress.contains(',') -> beforeAddress.splitAt(beforeAddress.lastIndexOf(',') + 1)
            else -> "" to beforeAddress
        }
    val person = name.replace(ZONE, "") + (address?.value ?: "")
    return person(person, date)
}

private fun String.splitAt(index: Int): Pair<String, String> = substring(0, index) to substring(index)

/** A person written as "Name <address>", "Name [mailto:address]", or an address alone. */
private fun person(
    written: String,
    date: String?,
): QuoteHeader {
    val address = (ADDRESS.find(written) ?: MAILTO.find(written))?.groupValues?.get(1)
    val name =
        written
            .replace(ADDRESS, "")
            .replace(MAILTO, "")
            .trim(' ', ',', '"', '\'')
    val bare = name.takeIf { '@' in it }
    val writtenDate =
        date
            ?.trim(' ', ',')
            ?.removeSuffix(" at")
            ?.ifBlank { null }
    return QuoteHeader(
        name = name.ifBlank { address.orEmpty() },
        address = address ?: bare,
        date = writtenDate?.let(::dateLabel),
        fullDate = writtenDate?.let(::fullDateLabel),
    )
}

private val SPACES = Regex("""\s+""")
private val ENGLISH = Regex("""^On (.+?),? wrote:$""")
private val GERMAN = Regex("""^Am (.+?) schrieb (.+?):$""")
private val GERMAN_TIME = Regex(""",? um \d{1,2}:\d{2}.*$""")
private val FIELD = Regex("""^(From|Von|Sent|Date|Gesendet|Datum|To|An|Cc|Subject|Betreff):\s*(.*)$""")
private val FROM_FIELDS = listOf("from", "von")
private val DATE_FIELDS = listOf("sent", "date", "gesendet", "datum")

/** One `@` splits the address, so a run of them is not tried at every one. */
private val ADDRESS = Regex("""<([^<>\s@]+@[^<>\s]+)>""")
private val MAILTO = Regex("""\[mailto:([^\]\s]+)]""")

/** Longer than any real attribution line, which names a date, a person, and an address. */
private const val MAX_ATTRIBUTION_LENGTH = 1_000
private val ZONE = Regex("""^\s*(?:[+-]\d{4}(?:\s*\([A-Z]{2,5}\))?|\([A-Z]{2,5}\)|UTC|GMT)(?=[\s,])""")
