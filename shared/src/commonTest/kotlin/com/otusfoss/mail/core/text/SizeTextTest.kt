package com.otusfoss.mail.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

class SizeTextTest {
    @Test
    fun aSizeThatRoundsUpToTheNextUnitUsesIt() {
        assertEquals("1 MB", sizeText(1_048_525))
        assertEquals("1 MB", sizeText(1_048_575))
        assertEquals("1 GB", sizeText(1_073_700_000))
    }

    @Test
    fun sizesBelowTheNextUnitKeepTheirOwn() {
        assertEquals("1023 bytes", sizeText(1_023))
        assertEquals("1 KB", sizeText(1_024))
        assertEquals("1023.9 KB", sizeText(1_048_524))
    }
}
