package com.otus.mail.feature.updates.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ChangeGroupTest {
    @Test
    fun userFacingCommitsGroupByScopeInTheOrderTheyAppear() {
        val builds =
            listOf(
                NightlyBuild(
                    142,
                    "",
                    listOf(
                        "- feat(sidebar): show the owl (#52)",
                        "- ci(nightly): build main",
                        "- fix(design-system): keep the bar still",
                    ),
                ),
                NightlyBuild(141, "", listOf("- perf(sidebar): draw less", "- fix: close the sheet", "")),
            )
        assertEquals(
            listOf(
                ChangeGroup("Sidebar", listOf("Show the owl", "Draw less")),
                ChangeGroup("Design system", listOf("Keep the bar still")),
                ChangeGroup("General", listOf("Close the sheet")),
            ),
            changesIn(builds),
        )
    }
}
