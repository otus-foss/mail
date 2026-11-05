# Inbox categories

Mail can sort the Inbox into category pills, like Gmail's tabs, Apple Mail's categories, and Proton
Mail's Categories. A category is a view over the Inbox: no message moves, no folder changes, and
nothing is written to the server. The feature is off until the user turns it on in **Mail behavior
› Inbox › Categories**. Mail sorts every message on the device, from what the device already holds.

## Categories

| Category | Holds |
| --- | --- |
| Primary | People, discussion lists, and anything Mail is unsure of. Always shown. |
| Transactions | Receipts, orders, bookings, bills, deliveries, and payments. |
| Updates | Account, security, and service notices, such as CI results. |
| Social | Social networks and community notifications. |
| Newsletters | Lists and newsletters the user signed up for, when they do not sell. |
| Promotions | Marketing and sales. |

The set matches Proton's, and is Apple's plus Social and Newsletters. Apple folds newsletters
into Updates and has no Social, which leaves a busy Updates; Gmail has no Transactions, which buries
receipts in Updates. Keeping newsletters apart from promotions follows Proton: mail the user asked
for reads differently from mail that sells. Social starts hidden, as most people get little of it
and a hidden category's mail simply stays in Primary.

The set is the `MailCategory` enum. Its order is the pills' order, and its `storageValue` is what
settings and the cache save, so renaming an entry keeps saved data. Adding a category means an
entry, a label and description in `CategoryLabels.kt`, an icon in `InboxPillChips.kt`, and the
rules that fill it.

## How a message is sorted

`CategoryClassifier` in `feature/inbox/domain/category` decides from `CategorySignals`. It is plain
common Kotlin: deterministic, no network, no model. Each step decides only when its evidence is
clear, and the first step that decides wins:

1. **Your choice for the sender.** "Categorize sender…" puts all mail from one address in a
   category, like Apple's Categorize Sender. It is applied when the list shows the message, not
   stored in the verdict, so changing it takes effect at once (`InboxCategories.placement`).
2. **schema.org markup** in an opened body, JSON-LD `@type` or microdata `itemtype`: `Order`,
   `Invoice`, `ParcelDelivery`, and the `Reservation` types are Transactions; `DiscountOffer` and
   `PromotionCard` are Promotions. Only the first 200,000 characters are read.
3. **Time-sensitive mail**: a sign-in or verification code, a password reset, a sign-in link, or a
   new sign-in alert, in the subject or the text, from mail without list headers. It goes to Updates
   and Primary shows it as well, like Apple does, so a code is never one tap away.
4. **The curated sender list** (`files/sender_categories.txt`): social networks, CI and account
   services, shops, payment providers, carriers, travel, and newsletter platforms. A listed shop's
   list mail without an order in its subject is Promotions; its receipts stay Transactions.
5. **`List-Post`**: a discussion list, where people write to each other, stays in Primary.
6. **List headers** (`List-Unsubscribe`, `List-Id`, `Precedence: bulk`, `list`, or `junk`): an
   order, delivery, or booking in the subject makes it a transaction, sales words a promotion, a
   `notifications@` or `alerts@` sender an update, and anything else a newsletter.
7. **`Auto-Submitted: auto-replied`**: an out-of-office reply from a person stays in Primary.
8. **Other automation**: `Auto-Submitted`, `Feedback-ID`, a bulk platform's header (Mailchimp,
   SendGrid, Amazon SES, Mailgun, Certified Senders Alliance), or a no-reply, orders, or billing
   address. An order or delivery in the subject or text makes it a transaction, anything else an
   update.
9. Anything left is **Primary**.

Keywords never decide alone. They only choose between categories once a header or the sender
already marks the mail as automated, so a friend's mail about "the invoice from the painter" stays
in Primary. A transaction word such as "receipt" or "invoice" also needs a number or an amount in
the subject; the phrases "has shipped" or "out for delivery" stand on their own. The word lists are
in `CategoryWords.kt` and are English only.

Every verdict keeps its reasons as `CategoryReason`s, a kind and the fact it rests on, such as the
header names, the schema.org type, the listed domain, or the words found. The Read view's ⋯ menu
offers **Why is this in Promotions?**, which opens a sheet with the reasons as sentences, for
example "In Promotions. It has mailing list headers: List-Unsubscribe. Its subject or text says
“% off”." The same sheet is **Categorize sender…**: Automatic, or one of the shown categories.

## What the device reads

The list already fetches each row's headers with `Email/get` (RFC 8621, section 4.1.3), so sorting
adds a few header properties to the same call and nothing else: `List-Post`, `Precedence`,
`Auto-Submitted`, `Feedback-ID`, and one header each of the bulk platforms above
(`CATEGORY_HEADER_PROPERTIES`). The text is the server's preview, or the body once the message was
opened. Mail never downloads a body to sort it. Mail without these headers, from a server that does
not return them, or cached before they were requested, is sorted from what it has.

## Storage and versions

`CategoryRecords` sorts when a list reads its rows from the cache, which every sync ends with, and
saves each verdict beside the email as `CacheKind.Category`, with its reasons and version. It
classifies at most 300 emails per read on `Dispatchers.Default`; the rest follow on the next read.
A verdict's version is the classifier's `CLASSIFIER_VERSION` and the sender list's `version`, so
bumping either sorts everything again. Opening a message drops its verdict, so its body's markup is
read on the next list read. Removing an account removes its verdicts with the rest of its cache.
While categories are off, nothing new is sorted.

The switch, the hidden categories, and the sender choices are saved with the other Mail behavior
settings (`inbox_categories`, `hidden_inbox_categories`, `sender_categories`), on the device only.

## Privacy

Sorting runs on the device, from mail already on the device. No message, header, address, or
verdict is sent anywhere, and no network request is made to sort. The sender list ships with the
app. Sender choices stay on the device and are not synced.

## In the Inbox

With categories on, the Inbox's pill row starts with one pill per shown category, Primary first,
each with its unread count, then the user's own pills. One category is selected at a time; pressing
the selected one returns to Primary. The selection only narrows the Inbox's own list: search and
folder pills show all of their mail, and folder pages have no category pills. A conversation's
category is that of its newest email in the Inbox.

## Known limits

- The sender list matches the From domain without checking DMARC, so a forged sender gets the
  real one's category. Categories never mark mail as trusted; the brand avatar still needs a DMARC
  pass.
- English words only. Mail in other languages falls back on headers and the sender list, which
  makes it Newsletters or Updates more often than Transactions or Promotions.
- Shops that send receipts with `List-Unsubscribe` and no order number in the subject read as
  newsletters or promotions until their markup is read or the user categorizes them.
- Replying to automated mail does not move it to Primary.
- A verdict follows the email the row shows; older emails of the conversation are not sorted.
- Unread counts cover the loaded pages, like the Unread pill.

## Follow-ups

- Sync sender choices through settings export and import, once Mail has it (#20).
- Store categories as JMAP keywords so other devices agree. Not done here, since no keyword is
  registered for it and other clients would show the raw keyword.
- Share the extraction with #28's message facts and let saved views (#4) and filters (#23) match on
  a category.

## Tests

`CategoryClassifierTest` runs realistic and adversarial samples from `CategoryFixtures`: a
newsletter that reads like a receipt, a person on a discussion list, a security code, an
out-of-office reply, a lookalike domain, and personal mail full of shop words. `InboxCategoriesTest`
covers the order of the user's choice, hidden categories, time-sensitive mail, unread counts, and
saving. `SenderDirectoryTest`, `StructuredMarkupTest`, and `CategoryLabelsTest` cover the sender
list, the markup scan and its time bound, and the reason sentences. `CategoryRecordsTest` covers the
cache and versions, `CategoryPersistenceTest` the Android settings store, and
`tools/tests/test_sender_categories.py` the shipped sender list's format.
