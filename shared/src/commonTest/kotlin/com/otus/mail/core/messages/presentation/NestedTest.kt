package com.otus.mail.core.messages.presentation

import com.otus.mail.core.settings.presentation.title
import kotlin.test.Test
import kotlin.test.assertEquals

class NestedTest {
    private fun nest(vararg paths: String) =
        paths.toList().nestedByPath { it.split(" / ") }.map { it.title to it.depth }

    @Test
    fun foldersSitUnderTheirParentsByName() {
        assertEquals(
            listOf("Clients" to 0, "Acme" to 1, "Invoices" to 2, "Beta" to 1, "Projects" to 0),
            nest("Clients", "Clients / Acme", "Clients / Acme / Invoices", "Clients / Beta", "Projects"),
        )
    }

    @Test
    fun aFolderWithoutItsParentKeepsTheMissingPath() {
        assertEquals(
            listOf("Clients / Acme" to 0, "Invoices" to 1, "Clients / Beta" to 0),
            nest("Clients / Acme", "Clients / Acme / Invoices", "Clients / Beta"),
        )
    }

    @Test
    fun aSimilarNameIsNotAParent() {
        assertEquals(listOf("Client" to 0, "Clients / Acme" to 0), nest("Client", "Clients / Acme"))
    }
}
