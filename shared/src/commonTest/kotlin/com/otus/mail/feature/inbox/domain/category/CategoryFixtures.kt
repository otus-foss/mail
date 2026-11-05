package com.otus.mail.feature.inbox.domain.category

// Realistic samples of each category, with the headers their senders really add, and samples
// made to fool the classifier. Bodies are only what the device would have: a preview, or the
// opened body's text and markup.

internal val TestSenders =
    SenderDirectory.parse(
        """
        # A small list in the shipped file's format.
        version 3
        notifications facebookmail.com
        notifications linkedin.com
        notifications github.com
        transactions amazon.com
        transactions paypal.com
        newsletters substack.com
        unknown example.net
        """.trimIndent(),
    )

internal val Classifier = CategoryClassifier(TestSenders)

internal fun classify(signals: CategorySignals): CategoryVerdict = Classifier.classify(signals)

/** A colleague writing to the user. */
internal val PersonalMail =
    CategorySignals(
        senderAddress = "ada@lovelace.example",
        subject = "Lunch on Friday?",
        text = "Are you free on Friday? The usual place at noon.",
    )

/** A shop's receipt with JSON-LD Order markup, sent through Amazon SES. */
internal val MarkedUpReceipt =
    CategorySignals(
        senderAddress = "orders@shop.example",
        subject = "Thanks for your purchase",
        markup = setOf("Order"),
        headers = BulkHeaders(feedbackId = "1:receipts:shop", platforms = listOf("Amazon SES")),
    )

/** A flight booking with FlightReservation microdata. */
internal val FlightBooking =
    CategorySignals(
        senderAddress = "service@airline.example",
        subject = "Your trip to Lisbon",
        markup = setOf("FlightReservation"),
    )

/** A delivery notice from a carrier's no-reply address, without markup. */
internal val ShippingNotice =
    CategorySignals(
        senderAddress = "no-reply@carrier.example",
        subject = "Your parcel is out for delivery",
    )

/** An invoice with its number in the subject, from a billing address. */
internal val NumberedInvoice =
    CategorySignals(
        senderAddress = "billing@hosting.example",
        subject = "Invoice INV-20431 for October",
        headers = BulkHeaders(feedbackId = "invoice:hosting"),
    )

/** A shop on the sender list sending its receipt. */
internal val KnownShopOrder =
    CategorySignals(
        senderAddress = "auto-confirm@amazon.com",
        subject = "Your Amazon.com order #112-4471 has shipped",
    )

/** The same shop's marketing list. */
internal val KnownShopSale =
    CategorySignals(
        senderAddress = "store-news@amazon.com",
        subject = "Deals picked for you",
        headers = BulkHeaders(listUnsubscribe = true, precedence = "bulk"),
    )

/** A marketing mail from a shop the list does not know. */
internal val SaleMail =
    CategorySignals(
        senderAddress = "hello@brand.example",
        subject = "30% off everything this weekend",
        headers = BulkHeaders(listUnsubscribe = true, platforms = listOf("Mailchimp")),
    )

/** A weekly newsletter. */
internal val Newsletter =
    CategorySignals(
        senderAddress = "digest@weekly.example",
        subject = "This week in Kotlin, issue 42",
        headers = BulkHeaders(listId = "Kotlin weekly <weekly.example>", listUnsubscribe = true),
    )

/** A newsletter whose subject reads like a receipt, but names no order or amount. */
internal val ReceiptLookingNewsletter =
    CategorySignals(
        senderAddress = "editor@news.example",
        subject = "Your receipt: the week's ten best stories",
        text = "Read the stories everyone talked about. Order of the day: coffee.",
        headers = BulkHeaders(listId = "<news.example>", listUnsubscribe = true, precedence = "Bulk"),
    )

/** A newsletter that sells and mentions an order. */
internal val SaleWithOrderWords =
    CategorySignals(
        senderAddress = "news@shop.example",
        subject = "Order now: 20% off your next order #1",
        headers = BulkHeaders(listUnsubscribe = true),
    )

/** A person's reply on a discussion list that adds List-Id, List-Unsubscribe, and List-Post. */
internal val DiscussionListReply =
    CategorySignals(
        senderAddress = "grace@hopper.example",
        subject = "Re: [compilers] Proposal for the new parser",
        headers =
            BulkHeaders(
                listId = "Compilers discussion <compilers.lists.example>",
                listUnsubscribe = true,
                listPost = "<mailto:compilers@lists.example>",
                precedence = "list",
            ),
    )

/** A social network's notification, with list headers of its own. */
internal val SocialNotification =
    CategorySignals(
        senderAddress = "notification@facebookmail.com",
        subject = "Ada commented on your photo",
        headers = BulkHeaders(listUnsubscribe = true),
    )

/** A CI result from GitHub, which marks it as a list. */
internal val CiFailure =
    CategorySignals(
        senderAddress = "notifications@github.com",
        subject = "[otus/mail] Run failed: quality - main (9ca82dd)",
        headers = BulkHeaders(listId = "otus/mail <mail.otus.github.com>", listUnsubscribe = true),
    )

/** A machine notice from an address the list does not know. */
internal val AccountNotice =
    CategorySignals(
        senderAddress = "accounts@cloud.example",
        subject = "Your storage plan changes next month",
        headers = BulkHeaders(autoSubmitted = "auto-generated"),
    )

/** A sign-in code. */
internal val SecurityCode =
    CategorySignals(
        senderAddress = "no-reply@bank.example",
        subject = "Your verification code",
        text = "Use 482913 to sign in. It expires in 10 minutes.",
        headers = BulkHeaders(autoSubmitted = "auto-generated"),
    )

/** A code in the subject itself, as some services send it. */
internal val CodeInSubject =
    CategorySignals(senderAddress = "security@social.example", subject = "482913 is your login code")

/** A password reset from a known shop. */
internal val ShopPasswordReset =
    CategorySignals(senderAddress = "account-update@amazon.com", subject = "Reset your password")

/** A newsletter about passwords, which is not time-sensitive. */
internal val PasswordNewsletter =
    CategorySignals(
        senderAddress = "tips@security.example",
        subject = "How to pick a password reset question",
        headers = BulkHeaders(listUnsubscribe = true),
    )

/** An out-of-office reply from a person. */
internal val OutOfOffice =
    CategorySignals(
        senderAddress = "ada@lovelace.example",
        subject = "Automatic reply: Lunch on Friday?",
        headers = BulkHeaders(autoSubmitted = "auto-replied"),
    )

/** A friend mentioning an invoice and a sale in personal mail. */
internal val PersonalWithTriggerWords =
    CategorySignals(
        senderAddress = "friend@mail.example",
        subject = "The sale on the house and the invoice from the painter",
        text = "Here's the invoice #2026 and the receipt. The order went through.",
    )

/** A phishing mail claiming a parcel, from a lookalike domain. */
internal val LookalikeDomain =
    CategorySignals(senderAddress = "support@amazon.com.parcel-check.example", subject = "Action needed")
