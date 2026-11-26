package com.otusfoss.mail.core.codes.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class SignInCodeFalseAlarmsTest {
    @Test
    fun mailThatNamesNoCodeHasNone() {
        assertEquals(null, codeIn("Lunch?", "Meet at 1230 by the door, room 4821."))
    }

    @Test
    fun otherNumbersBesideTheWordCodeAreNoCodes() {
        assertEquals(null, codeIn("Your order", "Use promo code SAVE2026 for 20% off."))
        assertEquals(null, codeIn("Delivery", "Postcode 1012AB, zip code 94107."))
        assertEquals(null, codeIn("Scan", "Scan the QR code 48291374 at the gate."))
        assertEquals(null, codeIn("Adresse", "Code postal : 75001"))
        assertEquals(null, codeIn("Pedido", "Código postal 28013"))
        assertEquals(null, codeIn("Gutschein", "Dein Gutscheincode: AB12CD34"))
    }

    @Test
    fun aWordInsideAPromotionCodeNamesNothing() {
        assertEquals(null, codeIn("Sale", "Save 20% with CODE2026!"))
        assertEquals(null, codeIn("Sale", "Use PIN1234 at checkout"))
    }

    @Test
    fun aCodeAndANumberInAnotherSentenceAreApart() {
        assertEquals(null, codeIn("Support", "Need a code? 4829 is our extension."))
        assertEquals(null, codeIn("Code", "Your code costs $482 913"))
    }

    @Test
    fun aNumberAnotherWordNamesIsNoCode() {
        assertEquals(null, codeIn("Your sign-in code", "Your code is in the app. Order number 482913."))
        assertEquals(null, codeIn("Your code", "Code: card ending 4829"))
        assertEquals(null, codeIn("Bestellung", "Ihr Code für die Bestellnummer 482913"))
    }

    @Test
    fun capitalsBesideTheWordCodeAreAPromotion() {
        assertEquals(null, codeIn("Sale", "Use code SUMMER for 20% off your next order."))
        assertEquals(null, codeIn("Sale", "Your code is SUMMERSALE"))
    }

    @Test
    fun aBookingReferenceIsNoCode() {
        assertEquals(null, codeIn("Your flight", "Confirmation code: ABC123"))
        assertEquals(null, codeIn("Your trip", "Booking code ABC123"))
    }

    @Test
    fun pricesYearsDatesTimesAndPhonesAreNoCodes() {
        assertEquals(null, codeIn("Code", "Your code costs $1234 or 1234.50 €."))
        assertEquals(null, codeIn("Code", "Your code expires in 2026."))
        assertEquals(null, codeIn("Code", "Your code expires on 26-11-2026 at 10:30."))
        assertEquals(null, codeIn("Code", "Your code? Call +1 415 555 0132."))
        assertEquals(null, codeIn("Code", "Your code? Call 0800 123 4567."))
    }

    @Test
    fun linksAndHashesAreNoCodes() {
        assertEquals(null, codeIn("Code", "Your code: https://example.com/verify?token=482913"))
        assertEquals(null, codeIn("Code", "Code color #482913"))
        assertEquals(null, codeIn("Code review", "Code pushed in commit 22eae99"))
    }

    @Test
    fun twoDifferentCodesGiveNone() {
        assertEquals(null, codeIn("Codes", "Your code is 482913.\nYour backup code is 739104."))
    }

    @Test
    fun repliesAndForwardsHaveNoCodeOfTheirOwn() {
        assertEquals(null, codeIn("Re: Your code", "Your code is 482913"))
        assertEquals(null, codeIn("Fwd: Your code", "Your code is 482913"))
        assertEquals(null, codeIn("AW: Ihr Code", "Ihr Code lautet 482913"))
    }

    @Test
    fun mailingListsHaveNoCode() {
        assertEquals(null, codeIn("Weekly", "Your code is 482913", fromList = true))
    }

    @Test
    fun wordsEndingInCodeThatNameNoCodeDoNotCount() {
        assertEquals(null, codeIn("Release", "Decode 482913 with the unicode table."))
    }
}
