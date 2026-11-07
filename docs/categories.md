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
| Notifications | Automated mail that does not sell: account and security alerts, CI, and social networks. |
| Newsletters & promotions | All other bulk mail. |

Each category rests on one or two signals that rules tell apart reliably, so the rules alone stay
precise (#239). Proton splits bulk mail into Newsletters and Promotions, and Gmail and Proton keep
Social apart; rules cannot do either well, since the same headers and senders send both kinds.
Those splits wait for the classifier that learns from the user's corrections in #239. Apple's
Updates holds both notifications and newsletters, so account alerts sit among marketing; keeping
Notifications apart from bulk mail follows Proton.

The set is the `MailCategory` enum. Its order is the pills' order, and its `storageValue` is what
settings and the cache save, so renaming an entry keeps saved data. Adding a category means an
entry, a label and description in `core/categories/presentation/CategoryLabels.kt`, an icon in
`InboxPillChips.kt`, and the rules that fill it. The user can hide any category but Primary; a hidden category's mail shows in
Primary.

## Where the code lives

- `core/categories`: what the Inbox, the Read view, and settings share. `domain` holds
  `MailCategory`, the classifier's `CategoryVerdict` and its reasons, and `InboxCategories`, the
  user's settings with `placement`, which applies the sender choices and hidden categories.
  `presentation` holds the labels, descriptions, and reason sentences.
- `feature/inbox/domain/category`: the classifier, its word lists, the sender list parser, and the
  schema.org scan. `feature/inbox/data`: `CategoryRecords`, which keeps verdicts in the cache, and
  `CategorizedMailSource`, which sorts each list read. `feature/inbox/presentation`: the pills,
  `CategoryTabs`, and the paging of a quiet pill.
- `feature/read`: the ⋯ menu entries and `CategorySheet`, through `ReadCategories`, which the app
  wires only for a list whose `ListBase` has categories, the Inbox.
- `feature/mailsettings`: the Categories row and `CategoriesSheet`, declared once for the screen
  and settings search.
- `core/messages`: each row carries its verdict as `MailMessage.category`. `core/settings`: the
  settings live in `ListBehavior.categories`.

## How a message is sorted

`CategoryClassifier` in `feature/inbox/domain/category` decides from `CategorySignals`. It is plain
common Kotlin: deterministic, no network, no model. Each step decides only when its evidence is
clear, and the first step that decides wins:

1. **Your choice for the sender.** "Categorize sender…" puts all mail from one address in a
   category, like Apple's Categorize Sender. It is applied when the list shows the message, not
   stored in the verdict, so changing it takes effect at once (`InboxCategories.placement`).
2. **schema.org markup** in an opened body, JSON-LD `@type` or microdata `itemtype`: `Order`,
   `Invoice`, `ParcelDelivery`, and the `Reservation` types are Transactions; `DiscountOffer` and
   `PromotionCard` are Newsletters & promotions. Only the first 200,000 characters are read.
3. **Time-sensitive mail**: a sign-in or verification code, a password reset, a sign-in link, or a
   new sign-in alert. Mail a machine sent (automation headers, a no-reply address, or a verified
   listed sender) counts with the words in its subject or text. Mail with list headers counts only
   with the words in its subject and a no-reply address or a code in the subject, so a bulk
   sender's code counts and a newsletter about passwords does not. Other mail counts only with a
   code beside the word "code" in its subject, so a colleague's "Code freeze 2026" stays in
   Primary. A code has four to eight digits and is never a year, a date, or an amount. It goes to
   Notifications, and Primary always shows it as well, whatever the user chose for the sender or
   hid (#239).
4. **The curated sender list** (`files/sender_categories.txt`): social networks, CI and account
   services, shops, payment providers, carriers, travel, and newsletter platforms. It counts only
   when the message passed DMARC for its From domain, the same check a brand logo needs, so a
   forged `paypal.com` sender is not filed as a payment. A listed shop's list mail without an order
   in its subject is bulk mail; its receipts stay Transactions.
5. **A code forge's header**: `X-GitHub-Reason`, `X-GitLab-Project`, `X-Gitea-Reason`, or
   `X-Forgejo-Reason` makes a notification, so self-hosted forges need no list entry. Forgejo
   sends both its own and Gitea's header.
6. **`List-Post`**: a discussion list, where people write to each other, stays in Primary.
7. **List headers** (`List-Unsubscribe`, `List-Id`, `Precedence: bulk`, `list`, or `junk`): a
   transaction phrase such as "boarding pass", or an order word with a number and no sales words,
   in the subject makes it a transaction, a `notifications@` or `alerts@` sender a notification,
   and anything else bulk mail.
8. **`Auto-Submitted: auto-replied`**: an out-of-office reply from a person stays in Primary.
9. **Other automation**: `Auto-Submitted`, `Feedback-ID`, a bulk platform's header (Mailchimp,
   SendGrid, Amazon SES, Mailgun, Certified Senders Alliance), or a no-reply, orders, or billing
   address. An order or delivery in the subject or text makes it a transaction, anything else a
   notification.
10. Anything left is **Primary**.

Keywords never decide alone. They only choose between categories once a header or the sender
already marks the mail as automated, so a friend's mail about "the invoice from the painter" stays
in Primary. A transaction word such as "receipt" or "invoice" also needs a number or an amount in
the subject; the phrases "has shipped" or "out for delivery" stand on their own. The word lists are
in `CategoryWords.kt` and are English only.

Every verdict keeps its reasons as `CategoryReason`s, a kind and the fact it rests on, such as the
header names, the schema.org type, the listed domain, or the words found. The Read view's ⋯ menu
offers **Why is this in Transactions?**, which opens a sheet with the reasons as sentences, for
example "In Newsletters & promotions. It has mailing list headers: List-Unsubscribe. Its subject
or text says “% off”." The same sheet is **Categorize sender…**: Automatic, or one of the shown
categories.

## What the device reads

The list already fetches each row's headers with `Email/get` (RFC 8621, section 4.1.3), so sorting
adds a few header properties to the same call and nothing else: `List-Post`, `Precedence`,
`Auto-Submitted`, `Feedback-ID`, one header each of the bulk platforms above, and the forge
headers (`CATEGORY_HEADER_PROPERTIES`). The text is the server's preview, or the body once the
message was opened. Mail never downloads a body to sort it. Mail without these headers, from a server that does
not return them, or cached before they were requested, is sorted from what it has.

## Storage and versions

`CategoryRecords` sorts when a list reads its rows from the cache, which every sync ends with, and
saves each verdict beside the email as `CacheKind.Category`, with its reasons and version. It
classifies the listed rows it has no verdict for on `Dispatchers.Default`. A verdict's version is
the classifier's `CLASSIFIER_VERSION` and the sender list's `version`, so bumping either sorts
everything again. A verdict stays as it was first listed while Mail runs, so opening a message
never moves it to another pill. Opening drops the saved verdict, so the body's markup counts from
the next start. Verdicts of mail received more than 90 days ago are trimmed with the bodies at
start, and sorted again when a list shows that mail. Removing an account removes its verdicts with
the rest of its cache. While categories are off, the mail source reads nothing for them and
nothing new is sorted. The platform entry point hands the switch to `MailServices` through
`MailPlatform.categoriesOn`, so lists read before any screen is shown sort as well.

The switch, the hidden categories, and the sender choices are saved with the other list settings
in `ListBehavior.categories` (`inbox_categories`, `hidden_inbox_categories`, `sender_categories`),
on the device only.

## Privacy

Sorting runs on the device, from mail already on the device. No message, header, address, or
verdict is sent anywhere, and no network request is made to sort. The sender list ships with the
app. Sender choices stay on the device and are not synced.

## In the Inbox

With categories on, the Inbox's pill row starts with one pill per shown category, Primary first,
each with its unread count, then the user's own pills. One category is selected at a time; pressing
the selected one returns to Primary. The selection only narrows the Inbox's own list: search and
folder pills show all of their mail, and folder pages have no category pills or category entries
in the Read view's ⋯ menu. A conversation's category is that of its newest email in the Inbox.

A pill shows only its share of the loaded pages, so it loads older pages while they add rows to
it, and an empty pill loads by itself. After three pages in a row add nothing, the pill stops
loading, so a quiet category does not page through the whole mailbox; choosing another pill
starts again.

## Known limits

- On a server that adds no `Authentication-Results`, the sender list never counts, and listed
  senders are sorted by their headers alone.
- English words only. Mail in other languages falls back on headers and the sender list, which
  makes it bulk mail or a notification more often than a transaction.
- Shops that send receipts with `List-Unsubscribe` and no order number in the subject read as bulk
  mail until their markup is read or the user categorizes them.
- Newsletters and promotions share a category, and social networks are notifications, until #239.
- Replying to automated mail does not move it to Primary.
- A verdict follows the email the row shows; older emails of the conversation are not sorted.
- Unread counts cover the loaded pages, like the Unread pill.
- A quiet pill stops loading older pages after three that add nothing to it.

## Follow-ups

- Sync sender choices through settings export and import, once Mail has it (#20).
- Store categories as JMAP keywords so other devices agree. Not done here, since no keyword is
  registered for it and other clients would show the raw keyword.
- Learn from the user's corrections on the device, and split Newsletters from Promotions and
  Social from Notifications once that is accurate (#239).
- Share the extraction with #28's message facts and let saved views (#4) and filters (#23) match on
  a category.

## Tests

`CategoryClassifierTest` runs realistic and adversarial samples from `CategoryFixtures`: a
newsletter that reads like a receipt, a person on a discussion list, security codes and password
resets with and without list headers, a newsletter about passwords, a self-hosted GitLab, an
out-of-office reply, a lookalike domain, and personal mail full of shop words.
`CategoryFalseAlarmsTest` keeps years, dates, and a person's words from making mail time-sensitive.
`InboxCategoriesTest` covers the order of the user's choice, hidden categories, time-sensitive
mail, unread counts, and the pills. `SenderDirectoryTest`, `StructuredMarkupTest`, and
`CategoryLabelsTest` cover the sender list, the markup scan and its time bound, and the reason
sentences. `CategoryRecordsTest` and `CategorizedMailSourceTest` cover the cache, versions, and
list reads, `CategoryPagingTest` a quiet pill's paging, `ReadMenuCategoriesTest` the ⋯ menu
entries, `CategoryPersistenceTest` the Android settings store, and
`tools/tests/test_sender_categories.py` the shipped sender list's format. `CategoriesScreenshotTest`
records the pills in the Otus, Light, and Black themes, the Categories sheet, and the Read view's
category sheet.
