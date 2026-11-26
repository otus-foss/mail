# One-time codes

Mail offers to copy the one-time code that sign-in and verification mail sends. The Read view
shows the code on a strip with **Copy**, and a new-mail alert shows **Copy code**; see
[notifications](notifications.md#buttons).

## Finding a code

`findSignInCode` in `core/codes/domain` reads a message's subject and each of its body texts on
their own: the plain part, and the HTML part read as text when the plain part is not already it.
A wrong code on the clipboard is worse than none, so it gives a code only when it finds exactly
one, after removing copies of the same code.

1. **Message checks.** Mail from a mailing list (`List-Id`) and replies and forwards, by their
   subject prefix, have no code. Mail that names no code anywhere has none.
2. **Reading.** Links go, digits of any script read as 0 to 9, every space and dash becomes a
   plain one, and invisible characters go. `htmlToText` already drops hidden elements, such as a
   preview line in `display:none`.
3. **Shapes.** 4 to 8 digits; two groups of three ("482 913", "482-913"); digits a space apart,
   as separate table cells come out ("4 8 2 9 1 3"); `G-` before digits, which Google writes; and
   5 to 10 letters and digits mixed ("K7M2QX", "tt226-5398x"), or two groups of capitals
   ("DJZ-TLX"). Years, prices, decimals, times, dates, phone numbers, hashes (`#482913`), and
   words with three lowercase letters in a row, such as commit hashes, are not codes.
4. **Places.** A shape counts only beside a word that names a code: up to four words after it
   ("Your verification code is 482913"), up to five before it ("482913 is your Facebook code"),
   or up to two after a word that asks to type it ("Enter 482913"). It also counts alone on its
   line, the way a code set in large type comes out, when the nearer of the two lines above or the
   line below names a code. A word for another kind of number, such as "order", "Rechnung", or
   "número", between them, or on that line, rules it out.

The words are in `Word.kt`, in English, German, French, Spanish, Portuguese, and Dutch.
"Code" also counts at the end of a compound ("Bestätigungscode", "verificatiecode"). Postcodes,
QR codes, promo codes, and the like do not count, nor does "code" after such a word or before
"postal" or "de descuento". A code with letters needs a word that is not about a booking or a
confirmation, since "Confirmation code: ABC123" is usually a flight.

## Where it shows

`toMessageContent` sets `MessageContent.signInCode` when a body loads. The Read view shows the
code strip in place of the context strip for that page: the code, "One-time code", and **Copy**.
Copy puts the code on the clipboard without its spaces, marked sensitive, and the strip turns into
"Copied" with **Delete**, which runs the bar's Delete. An alert finds its code in the subject and
the preview alone, since it has no body.

## Not done yet

- A test set of real mail, to measure how many codes the rules catch and how many they invent.
- Words in more languages, once there are real samples of their mail.
- The `OTP-Token` header that some senders add.
