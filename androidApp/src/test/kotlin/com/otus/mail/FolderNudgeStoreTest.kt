package com.otus.mail

import com.otus.mail.feature.folders.domain.FolderKey
import com.otus.mail.feature.folders.domain.FolderNudges
import com.otus.mail.feature.folders.domain.readNudges
import com.otus.mail.feature.folders.domain.writeNudges
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FolderNudgeStoreTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun nudgesSurviveReopeningTheStore() {
        // Login ids hold a space and the session URL's colons.
        val saved =
            FolderNudges(
                setOf(
                    FolderKey("ada https://mail.example.org/jmap/session", "M1"),
                    FolderKey("ada https://mail.example.org/jmap/session", "M2"),
                    FolderKey("bob https://mail.example.com/jmap/session", "M1"),
                ),
            )
        testStores(context)
            .folders.nudges
            .let(::checkNotNull)
            .writeNudges(saved)
        assertEquals(
            saved,
            testStores(context)
                .folders.nudges
                .let(::checkNotNull)
                .readNudges(),
        )
    }
}
