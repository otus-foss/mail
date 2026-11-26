package com.otusfoss.mail.core.codes.domain

/**
 * A word on a line, lowercase, at [range] of the line. [names] when it names a one-time code, such
 * as "code", "OTP", or "Bestätigungscode". [namesLetterCode] when it may also name one with letters,
 * which a booking's or a confirmation's reference often is. [command] when it asks to type a code,
 * as "Enter" does, and [warns] when it names another kind of number, such as an order's.
 */
internal class Word(
    val text: String,
    val range: IntRange,
    val names: Boolean,
    val namesLetterCode: Boolean,
    val command: Boolean,
    val warns: Boolean,
)

/** The words of [line] at their places, each judged with the words around it. */
internal fun words(line: String): List<Word> {
    val found = WORD.findAll(line).toList()
    val texts = found.map { it.value.lowercase() }
    return found.mapIndexed { index, match ->
        val text = texts[index]
        val near = Near(texts.getOrNull(index - 1), texts.drop(index + 1).take(WORDS_AFTER))
        val names = namesCode(text, near)
        Word(
            text = text,
            range = match.range,
            names = names,
            namesLetterCode = names && !doubtsLetters(text, near),
            command = text in COMMANDS,
            warns = text in WARNINGS || WARNING_ENDINGS.any(text::endsWith),
        )
    }
}

/** The word before a word, and the two after it. */
private class Near(
    val before: String?,
    val after: List<String>,
)

/** Whether [word] names a one-time code where it stands, and not a postcode, a promo code, or a QR code. */
private fun namesCode(
    word: String,
    near: Near,
): Boolean =
    when {
        word in LABELS -> true
        word == "password" || word == "passwort" -> near.before == "time" || near.before == "einmal"
        codePrefix(word) == null || word in NOT_CODE_WORDS -> false
        else -> codePrefix(word) !in NOT_CODES && near.before !in NOT_CODES && !qualifiedAfter(near, NOT_CODES)
    }

/** Whether a code [word] names may be a booking's or a confirmation's reference, when it has letters. */
private fun doubtsLetters(
    word: String,
    near: Near,
): Boolean =
    codePrefix(word) in LETTER_DOUBTS ||
        near.before in LETTER_DOUBTS ||
        qualifiedAfter(near, LETTER_DOUBTS)

/** What comes before "code" in [word]: "" for "code", "bestätigungs" for "Bestätigungscode"; null without one. */
private fun codePrefix(word: String): String? = CODE_ENDINGS.firstOrNull(word::endsWith)?.let { word.removeSuffix(it) }

/** "code postal", "código de descuento": a word of [words] after the code word, or after its "de". */
private fun qualifiedAfter(
    near: Near,
    words: Set<String>,
): Boolean {
    val next = near.after.firstOrNull()
    return next in words || (next in LINKS && near.after.getOrNull(1) in words)
}

private val WORD = Regex("""\p{L}+""")

/** How many words after a code word can say what kind of code it is: "código de descuento". */
private const val WORDS_AFTER = 2

/** The word for "code" in English, German, Dutch, French, Spanish, and Portuguese, also ending a compound. */
private val CODE_ENDINGS = listOf("codes", "code", "códigos", "código", "codigos", "codigo")

/** Words that name a code without the word "code". */
private val LABELS = setOf("otp", "pin", "tan", "mtan", "passcode", "passcodes", "einmalpasswort", "einmalkennwort")

/** "de" and its kin, which join a code word to what it is for: "code de vérification", "código de descuento". */
private val LINKS = setOf("de", "do", "da", "du", "di")

/** Codes that are not one-time codes, beside the word "code" or before it in a compound. */
private val NOT_CODES =
    setOf(
        "bar",
        "post",
        "postal",
        "zip",
        "area",
        "country",
        "qr",
        "source",
        "promo",
        "promotion",
        "promotional",
        "discount",
        "coupon",
        "voucher",
        "gift",
        "referral",
        "invite",
        "invitation",
        "error",
        "status",
        "dress",
        "tracking",
        "morse",
        "html",
        "css",
        "sample",
        "example",
        "embed",
        "color",
        "colour",
        "colors",
        "rabatt",
        "gutschein",
        "aktions",
        "aktion",
        "länder",
        "fehler",
        "quell",
        "programm",
        "strich",
        "geschenk",
        "korting",
        "kortings",
        "actie",
        "fout",
        "bron",
        "cadeau",
        "land",
        "landen",
        "réduction",
        "reduction",
        "suivi",
        "barres",
        "pays",
        "parrainage",
        "promocional",
        "descuento",
        "desconto",
        "rastreo",
        "rastreio",
        "seguimiento",
        "barras",
        "país",
        "área",
        "regalo",
        "presente",
        "indicação",
    )

/** Words that end in "code" and name no code at all. */
private val NOT_CODE_WORDS = setOf("decode", "decodes", "encode", "encodes", "unicode", "opcode", "opcodes", "barcode")

/** Words that make a code with letters more likely a booking's or a confirmation's reference. */
private val LETTER_DOUBTS =
    setOf(
        "confirmation",
        "booking",
        "reservation",
        "reference",
        "record",
        "pnr",
        "ticket",
        "buchungs",
        "buchung",
        "reservierungs",
        "bestätigungs",
        "referenz",
        "boekings",
        "boeking",
        "reserverings",
        "bevestigings",
        "réservation",
        "référence",
        "confirmación",
        "reserva",
        "referencia",
        "confirmação",
        "referência",
    )

/** Words that ask the reader to type a code: "Enter 482913 on the sign-in page". */
private val COMMANDS =
    setOf(
        "enter",
        "use",
        "type",
        "input",
        "paste",
        "gib",
        "geben",
        "eingeben",
        "verwende",
        "verwenden",
        "voer",
        "gebruik",
        "vul",
        "saisissez",
        "saisir",
        "entrez",
        "entrer",
        "utilisez",
        "utiliser",
        "tapez",
        "ingresa",
        "ingrese",
        "introduce",
        "introduzca",
        "introducir",
        "usa",
        "utiliza",
        "escribe",
        "digite",
        "insira",
        "introduza",
        "informe",
        "utilize",
    )

/** Words that name another kind of number near a code: an order's, an invoice's, a phone's. */
private val WARNINGS =
    setOf(
        "order",
        "invoice",
        "tracking",
        "reference",
        "ref",
        "booking",
        "reservation",
        "ticket",
        "case",
        "customer",
        "policy",
        "serial",
        "model",
        "version",
        "build",
        "issue",
        "commit",
        "port",
        "error",
        "phone",
        "tel",
        "telephone",
        "fax",
        "mobile",
        "call",
        "iban",
        "card",
        "ending",
        "ends",
        "total",
        "amount",
        "price",
        "qty",
        "quantity",
        "sku",
        "item",
        "transaction",
        "receipt",
        "postal",
        "zip",
        "postcode",
        "bestellung",
        "rechnung",
        "sendung",
        "buchung",
        "kunde",
        "kunden",
        "referenz",
        "telefon",
        "karte",
        "endet",
        "betrag",
        "summe",
        "preis",
        "artikel",
        "vorgang",
        "auftrag",
        "plz",
        "commande",
        "facture",
        "colis",
        "réservation",
        "référence",
        "client",
        "téléphone",
        "carte",
        "montant",
        "prix",
        "dossier",
        "pedido",
        "factura",
        "envío",
        "reserva",
        "referencia",
        "cliente",
        "teléfono",
        "tarjeta",
        "importe",
        "precio",
        "fatura",
        "encomenda",
        "referência",
        "telefone",
        "cartão",
        "valor",
        "preço",
        "bestelling",
        "factuur",
        "zending",
        "boeking",
        "reservering",
        "referentie",
        "klant",
        "telefoon",
        "kaart",
        "bedrag",
        "prijs",
    )

/** Endings of words for "number", which a code is rarely called: "Bestellnummer", "número de pedido". */
private val WARNING_ENDINGS = listOf("nummer", "number", "numbers", "número", "numéro", "numero")
