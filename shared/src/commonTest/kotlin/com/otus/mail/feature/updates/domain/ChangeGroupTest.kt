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

    @Test
    fun scopeSpellingsShareAGroupNamedByTheirWordBreaks() {
        val builds =
            listOf(
                NightlyBuild(
                    142,
                    "",
                    listOf(
                        "- feat(designsystem): give rows cards (#77)",
                        "- fix(design-system): make corners rounder (#59)",
                        "- feat(Design-System): add pills",
                    ),
                ),
            )
        assertEquals(
            listOf(ChangeGroup("Design system", listOf("Give rows cards", "Make corners rounder", "Add pills"))),
            changesIn(builds),
        )
    }

    @Test
    fun aChangeListedByMailAndMetaAppearsOnce() {
        val builds =
            listOf(
                NightlyBuild(
                    142,
                    "",
                    listOf(
                        "- feat(accounts): add an avatar for each own address (#142)",
                        "- feat(inbox): show cards (#141)",
                        "- feat(accounts): add an avatar for each own address (#79)",
                        "- feat(avatar): add an avatar for each own address (#80)",
                    ),
                ),
            )
        assertEquals(
            listOf(
                ChangeGroup("Accounts", listOf("Add an avatar for each own address")),
                ChangeGroup("Inbox", listOf("Show cards")),
            ),
            changesIn(builds),
        )
    }
}
