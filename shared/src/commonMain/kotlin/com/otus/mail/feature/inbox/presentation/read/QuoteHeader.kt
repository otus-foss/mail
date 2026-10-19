package com.otus.mail.feature.inbox.presentation.read

import com.otus.mail.feature.inbox.presentation.components.dayAndMonth
import kotlinx.datetime.LocalDate

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
 * "Am <date> schrieb <name>:". Null for any other line.
 */
internal fun attributionHeader(line: String): QuoteHeader? {
    val text = line.trim().replace(SPACES, " ")
    ENGLISH.matchEntire(text)?.let { return dateThenPerson(it.groupValues[1]) }
    return GERMAN.matchEntire(text)?.let { match ->
        val date = match.groupValues[1].replace(GERMAN_TIME, "")
        person(match.groupValues[2], date)
    }
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
    val written =
        date
            ?.trim(' ', ',')
            ?.removeSuffix(" at")
            ?.ifBlank { null }
    return QuoteHeader(
        name = name.ifBlank { address.orEmpty() },
        address = address ?: bare,
        date = written?.let(::dateLabel),
        fullDate = written?.let(::fullDateLabel),
    )
}

/** [written] as "10 Oct 2026, 17:50" when it names a day, month, and year; otherwise as written. */
private fun fullDateLabel(written: String): String =
    parseDate(written)?.let { day ->
        "${day.dayAndMonth} ${day.year}" + TIME.find(written)?.let { ", ${it.value}" }.orEmpty()
    } ?: written

/** [written] as "10 Oct" when it names a day, month, and year; otherwise as written. */
internal fun dateLabel(written: String): String = parseDate(written)?.dayAndMonth ?: written

private fun parseDate(written: String): LocalDate? =
    ISO_DATE.find(written)?.destructured?.let { (year, month, day) -> date(year, month, day) }
        ?: DOTTED_DATE.find(written)?.destructured?.let { (day, month, year) -> date(year, month, day) }
        ?: slashedDate(written)
        ?: namedMonthDate(written)

/**
 * A date such as "10/17/26", as Proton Mail writes it, read only when one number is past 12 and so
 * can only be the day; "5/6/26" stays as written, since it could be either order.
 */
private fun slashedDate(written: String): LocalDate? {
    val (first, second, year) = SLASHED_DATE.find(written)?.destructured ?: return null
    val fullYear = if (year.length == 2) "20$year" else year
    return when {
        first.toInt() > MONTHS_IN_YEAR -> date(fullYear, second, first)
        second.toInt() > MONTHS_IN_YEAR -> date(fullYear, first, second)
        else -> null
    }
}

/** A date with the month's name, such as "Thu, Oct 10, 2026" or "10 Oct 2026". */
private fun namedMonthDate(written: String): LocalDate? {
    val month = MONTH.find(written)?.let { found -> MONTHS.indexOfFirst { found.groupValues[1].lowercase() in it } + 1 }
    val year = YEAR.find(written)?.value
    val day = DAY.findAll(written).map { it.value }.firstOrNull { it != year }
    return if (month == null || year == null || day == null) null else date(year, month.toString(), day)
}

private fun date(
    year: String,
    month: String,
    day: String,
): LocalDate? = runCatching { LocalDate(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()

private val SPACES = Regex("""\s+""")
private val ENGLISH = Regex("""^On (.+?),? wrote:$""")
private val GERMAN = Regex("""^Am (.+?) schrieb (.+?):$""")
private val GERMAN_TIME = Regex(""",? um \d{1,2}:\d{2}.*$""")
private val FIELD = Regex("""^(From|Von|Sent|Date|Gesendet|Datum|To|An|Cc|Subject|Betreff):\s*(.*)$""")
private val FROM_FIELDS = listOf("from", "von")
private val DATE_FIELDS = listOf("sent", "date", "gesendet", "datum")
private val ADDRESS = Regex("""<([^<>\s]+@[^<>\s]+)>""")
private val MAILTO = Regex("""\[mailto:([^\]\s]+)]""")
private val TIME = Regex("""\d{1,2}:\d{2}(?::\d{2})?(?:\s?[AaPp]\.?[Mm]\.?)?""")
private val ZONE = Regex("""^\s*(?:[+-]\d{4}(?:\s*\([A-Z]{2,5}\))?|\([A-Z]{2,5}\)|UTC|GMT)(?=[\s,])""")
private val ISO_DATE = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
private val DOTTED_DATE = Regex("""\b(\d{1,2})\.(\d{1,2})\.(\d{4})\b""")
private val SLASHED_DATE = Regex("""\b(\d{1,2})/(\d{1,2})/(\d{4}|\d{2})\b""")
private const val MONTHS_IN_YEAR = 12
private val MONTHS =
    listOf("jan", "feb", "mar|mär", "apr", "may|mai", "jun", "jul", "aug", "sep", "oct|okt", "nov", "dec|dez")
        .map { it.split('|') }
private val MONTH =
    Regex("""\b(${MONTHS.flatten().joinToString("|")})[a-zä]*\.?(?![a-z])""", RegexOption.IGNORE_CASE)
private val YEAR = Regex("""\b\d{4}\b""")
private val DAY = Regex("""(?<![:\d])\b\d{1,2}\b(?![:\d])""")
