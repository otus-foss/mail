package com.otusfoss.mail.core.messages.domain

import com.otusfoss.mail.feature.compose.domain.startDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MailtoLinkTest {
    private fun person(address: String) = MailSender("", address)

    @Test
    fun aBareAddressGoesToTo() {
        assertEquals(
            ComposeRequest.New(to = listOf(person("chris@example.com"))),
            mailtoRequest("mailto:chris@example.com"),
        )
    }

    @Test
    fun everyFieldOfRfc6068FillsTheDraft() {
        val request =
            mailtoRequest(
                "mailto:a@example.com,b@example.com?to=c@example.com&cc=d@example.com" +
                    "&bcc=e@example.com&subject=Lunch%20plans&body=Noon%3F%0D%0ASee%20you",
            )
        assertEquals(
            ComposeRequest.New(
                to = listOf(person("a@example.com"), person("b@example.com"), person("c@example.com")),
                cc = listOf(person("d@example.com")),
                bcc = listOf(person("e@example.com")),
                subject = "Lunch plans",
                body = "Noon?\nSee you",
            ),
            request,
        )
    }

    @Test
    fun anEmptyTargetTakesItsRecipientsFromFields() {
        // RFC 6068, section 6.1: mailto:?to=joe@example.com&cc=bob@example.com&body=hello
        val request = mailtoRequest("mailto:?to=joe@example.com&cc=bob@example.com&body=hello")
        assertEquals(listOf(person("joe@example.com")), request?.to)
        assertEquals(listOf(person("bob@example.com")), request?.cc)
        assertEquals("hello", request?.body)
    }

    @Test
    fun fieldNamesAndTheSchemeIgnoreCase() {
        val request = mailtoRequest("MAILTO:a@example.com?SUBJECT=Hi&Cc=b@example.com")
        assertEquals("Hi", request?.subject)
        assertEquals(listOf(person("b@example.com")), request?.cc)
    }

    @Test
    fun escapedUtf8DecodesWholeAndPlusStaysAPlus() {
        // RFC 6068, section 6.2 encodes non-ASCII text as percent-escaped UTF-8.
        val request = mailtoRequest("mailto:user+tag@example.org?subject=%E2%98%95%20caf%C3%A9&body=1+1")
        assertEquals(listOf(person("user+tag@example.org")), request?.to)
        assertEquals("☕ café", request?.subject)
        assertEquals("1+1", request?.body)
    }

    @Test
    fun anEscapedAddressAndNamedRecipientsAreRead() {
        val request = mailtoRequest("mailto:grace%40example.org?to=Ada%20Lovelace%20%3Cada%40example.org%3E")
        assertEquals(listOf(person("grace@example.org"), MailSender("Ada Lovelace", "ada@example.org")), request?.to)
    }

    @Test
    fun repeatedAddressFieldsAddUpAndTheFirstSubjectWins() {
        val request = mailtoRequest("mailto:?cc=a@example.com&cc=b@example.com&subject=One&subject=Two")
        assertEquals(listOf(person("a@example.com"), person("b@example.com")), request?.cc)
        assertEquals("One", request?.subject)
    }

    @Test
    fun headersALinkShouldNotSetAreLeftOut() {
        val request = mailtoRequest("mailto:a@example.com?from=evil@example.com&in-reply-to=%3Cx%40y%3E&subject=Hi")
        assertEquals(ComposeRequest.New(to = listOf(person("a@example.com")), subject = "Hi"), request)
    }

    @Test
    fun aFragmentAndALoneEscapeDoNotBreakTheLink() {
        val request = mailtoRequest("mailto:a@example.com?subject=100%25%20sure%2&body=x#top")
        assertEquals("100% sure%2", request?.subject)
        assertEquals("x", request?.body)
    }

    @Test
    fun otherSchemesAreNotMailtoLinks() {
        assertNull(mailtoRequest("https://example.com/?to=a@example.com"))
        assertNull(mailtoRequest("a@example.com"))
        assertNull(mailtoRequest(""))
    }

    @Test
    fun aMailtoRequestStartsItsDraftFilledIn() {
        val request = mailtoRequest("mailto:a@example.com?cc=b@example.com&bcc=c@example.com&subject=Hi&body=Hello")
        assertEquals(
            Draft(
                "a1",
                to = listOf(person("a@example.com")),
                cc = listOf(person("b@example.com")),
                bcc = listOf(person("c@example.com")),
                subject = "Hi",
                body = "Hello",
            ),
            request?.startDraft("a1", emptyList()),
        )
    }
}
