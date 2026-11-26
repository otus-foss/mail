package com.otusfoss.mail.core.codes.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class SignInCodeTest {
    @Test
    fun aCodeAfterItsLabelCounts() {
        assertEquals("482913", codeIn("Your Acme code", "Your verification code is 482913."))
        assertEquals("482913", codeIn("Sign in", "Verification code: 482913"))
        assertEquals("482913", codeIn("Sign in", "Use code 482913 to sign in."))
    }

    @Test
    fun aCodeBeforeItsLabelCounts() {
        assertEquals("847291", codeIn("847291 is your one-time password"))
        assertEquals("482913", codeIn("482913 is your Acme code"))
        assertEquals("282913", codeIn("282913 is your Facebook confirmation code"))
    }

    @Test
    fun aCodeAfterACommandCountsInMailThatNamesACode() {
        assertEquals("482913", codeIn("Your sign-in code", "Enter 482913 on the sign-in page."))
    }

    @Test
    fun aCommandAloneIsNotEnough() {
        assertEquals(null, codeIn("Welcome", "Enter 4821 at the gate."))
    }

    @Test
    fun aCodeSplitInTwoGroupsIsTypedWhole() {
        assertEquals("482913", codeIn("Your code", "Your code is 482 913"))
        assertEquals("482913", codeIn("Your code", "Your code is 482-913"))
    }

    @Test
    fun codesWithLettersCount() {
        assertEquals("K7M2QX", codeIn("Your code", "Your code is K7M2QX"))
        assertEquals("tt226-5398x", codeIn("Log in to Linear", "Your login code is tt226-5398x"))
        assertEquals("DJZ-TLX", codeIn("Slack confirmation", "Your verification code: DJZ-TLX"))
    }

    @Test
    fun aCodeOfCapitalsAloneOnItsLineCounts() {
        val porkbun = "Hello there,\n\nYour 2FA code is:\n\nZKSRHMMHZO\n\nThank you,\nThe Porkbun Team"
        assertEquals("ZKSRHMMHZO", codeIn("porkbun.com | 2FA Code", porkbun))
    }

    @Test
    fun googleCodesLoseTheirPrefix() {
        assertEquals("482913", codeIn("G-482913 is your Google verification code"))
    }

    @Test
    fun aCodeSetAloneUnderItsLabelCounts() {
        val aws = "Verification code\n\n482913\n\n(This code is valid for 10 minutes)"
        assertEquals("482913", codeIn("AWS Email Verification", aws))
        assertEquals("482913", codeIn("Confirm your email", "Copy this code:\n“482913”"))
    }

    @Test
    fun aCodeSetAloneOverItsLabelCounts() {
        assertEquals("482913", codeIn("Sign in", "482913\nThat is your sign-in code."))
    }

    @Test
    fun digitsInSeparateCellsAreOneCode() {
        assertEquals("482913", codeIn("Verify your email", "Your verification code\n4 8 2 9 1 3"))
    }

    @Test
    fun theSameCodeInSubjectAndBodyIsOneCode() {
        assertEquals("482913", codeIn("482913 is your code", "Your code: 482 913", "Code\n482913"))
    }

    @Test
    fun otherLanguagesNameCodesToo() {
        assertEquals("482913", codeIn("Anmeldung", "Ihr Bestaetigungscode lautet 482913"))
        assertEquals("482913", codeIn("Anmeldung", "Ihr Bestätigungscode lautet 482913"))
        assertEquals("482913", codeIn("Connexion", "Votre code de vérification est 482913"))
        assertEquals("482913", codeIn("Inicio de sesión", "Tu código de verificación es 482913"))
        assertEquals("482913", codeIn("Acesso", "Seu código de acesso: 482913"))
        assertEquals("482913", codeIn("Inloggen", "Je verificatiecode is 482913"))
        assertEquals("482913", codeIn("Login", "Ihre TAN: 482913"))
    }

    @Test
    fun digitsOfOtherScriptsReadAsDigits() {
        assertEquals("482913", codeIn("Code", "Your code is ٤٨٢٩١٣"))
        assertEquals("482913", codeIn("Code", "Your code is ４８２９１３"))
    }

    @Test
    fun invisibleCharactersAndOddSpacesDoNotHideACode() {
        assertEquals("482913", codeIn("Code", "Your code is 4\u206382\u2063913"))
        assertEquals("482913", codeIn("Code", "Your code is 482\u00A0913"))
    }

    @Test
    fun sixAndEightDigitsReadInGroups() {
        assertEquals("482 913", SignInCode("482913").shown)
        assertEquals("4829 1374", SignInCode("48291374").shown)
        assertEquals("K7M2QX", SignInCode("K7M2QX").shown)
    }
}
