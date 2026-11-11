package com.otusfoss.mail.core.files

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SafeFileNameTest {
    @Test
    fun hiddenAndPathCharactersAreRemoved() {
        listOf(
            "photo‮gpj.exe" to "photogpj.exe",
            "re⁦port‏.pdf" to "report.pdf",
            "nul\u0000l.txt" to "null.txt",
            "../../.kept" to "_.._.kept",
            "a\\b/c.txt" to "a_b_c.txt",
            ".hidden" to "hidden",
            ".." to "attachment",
            " . " to "attachment",
            "" to "attachment",
        ).forEach { (name, expected) -> assertEquals(expected, safeFileName(name), name) }
    }

    @Test
    fun aLongNameIsCutBeforeItsExtension() {
        val cut = safeFileName("a".repeat(300) + ".pdf")
        assertEquals("a".repeat(196) + ".pdf", cut)
        val wide = safeFileName("ü".repeat(300) + ".docx")
        assertTrue(wide.encodeToByteArray().size <= 200, wide)
        assertTrue(wide.endsWith("ü.docx"), wide)
        val emoji = safeFileName("😀".repeat(100))
        assertEquals("😀".repeat(50), emoji, "A character is never split")
    }
}
