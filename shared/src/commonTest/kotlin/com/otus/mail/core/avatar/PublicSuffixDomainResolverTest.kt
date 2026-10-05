package com.otus.mail.core.avatar

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicSuffixDomainResolverTest {
    private val rules = PublicSuffixRules("com\nme\nuk\nco.uk\nio\ngithub.io\nck\n*.ck\n!www.ck")

    @Test
    fun mailSubdomainsUseTheWebsiteAndMailProvidersRemainEligible() {
        assertEquals("openai.com", rules.registrableDomain("email.openai.com"))
        assertEquals("proton.me", rules.registrableDomain("proton.me"))
        assertEquals("company.co.uk", rules.registrableDomain("email.company.co.uk"))
        assertNull(rules.registrableDomain("co.uk"))
    }

    @Test
    fun privateHostingSuffixesDoNotMergeIndependentSites() {
        assertEquals("ada.github.io", rules.registrableDomain("email.ada.github.io"))
        assertNull(rules.registrableDomain("github.io"))
    }

    @Test
    fun wildcardAndExceptionRulesUseTheLongestMatch() {
        assertEquals("sender.site.ck", rules.registrableDomain("sender.site.ck"))
        assertNull(rules.registrableDomain("site.ck"))
        assertEquals("www.ck", rules.registrableDomain("mail.www.ck"))
    }

    @Test
    fun unknownSuffixesUseTheDefaultRuleAndUnsafeHostsAreRejected() {
        assertEquals("company.example", rules.registrableDomain("mail.company.example"))
        assertEquals("openai.com", rules.registrableDomain("EMAIL.OPENAI.COM"))
        listOf("localhost", "a.internal", "127.0.0.1", "a..com", "a.com/path")
            .forEach { assertNull(rules.registrableDomain(it)) }
    }

    @Test
    fun commentsWhitespaceAndAsciiIdnRulesAreSupported() {
        val parsed = PublicSuffixRules("// comment\n\n  com  // note\nco.uk\tcomment\nxn--p1ai")
        assertEquals("company.co.uk", parsed.registrableDomain("mail.company.co.uk"))
        assertEquals("site.xn--p1ai", parsed.registrableDomain("email.site.xn--p1ai"))
    }

    @Test
    fun concurrentLookupsLoadTheBundledRulesOnce() =
        runTest {
            var loads = 0
            val resolver =
                PublicSuffixDomainResolver {
                    loads++
                    "com\nme"
                }
            val first = async { resolver.resolve("email.openai.com") }
            val second = async { resolver.resolve("proton.me") }
            assertEquals("openai.com", first.await())
            assertEquals("proton.me", second.await())
            assertEquals(1, loads)
        }
}
