package com.otus.mail.feature.folders.presentation

import com.otus.designsystem.theme.FolderColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextFolderColorTest {
    private val colors = FolderColor.entries - FolderColor.Default

    @Test
    fun theFirstUnusedColorComesNext() {
        assertEquals(FolderColor.Red, nextFolderColor(emptyList()))
        assertEquals(FolderColor.Yellow, nextFolderColor(listOf("Red", "Orange", "Teal")))
    }

    @Test
    fun onceEveryColorIsUsedTheyRepeatInOrder() {
        val all = colors.map { it.name }
        assertEquals(FolderColor.Red, nextFolderColor(all))
        assertEquals(FolderColor.Orange, nextFolderColor(all + "Red"))
    }

    @Test
    fun unknownNamesAndDefaultAreIgnored() {
        assertEquals(FolderColor.Red, nextFolderColor(listOf("Default", "Mauve")))
    }

    @Test
    fun aNewFolderStartsWithTheNextColorOnlyWhileColorsShow() {
        var shown = true
        val state = FolderColorsState { shown }
        assertEquals("Red", state.newFolderColor())
        shown = false
        assertNull(state.newFolderColor())
    }
}
