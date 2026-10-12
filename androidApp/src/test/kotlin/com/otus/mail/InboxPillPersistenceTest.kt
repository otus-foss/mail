package com.otus.mail

import android.content.Context
import androidx.core.content.edit
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.ActionBarMode
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailBehavior
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InboxPillPersistenceTest {
    @Test
    fun existingReadLaterSettingsGetTheirDefaultPills() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("otus_mail_behavior", Context.MODE_PRIVATE).edit {
            clear()
            putBoolean("read_later", true)
        }
        val restored = AndroidMailBehaviorStore(context).read()
        assertEquals(InboxPill.defaults(readLater = true), restored.pills)
    }

    @Test
    fun reorderedPillsAndAnEmptyRowSurviveReopeningTheStore() {
        val context = RuntimeEnvironment.getApplication()
        val saved =
            MailBehavior(
                readLater = true,
                pills = listOf(InboxPill.Role("archive"), InboxPill.Filter.Flagged, InboxPill.ReadLater),
                combinedFolders = true,
            )
        AndroidMailBehaviorStore(context).write(saved)
        assertEquals(saved, AndroidMailBehaviorStore(context).read())
        AndroidMailBehaviorStore(context).write(saved.copy(pills = emptyList()))
        assertEquals(saved.copy(pills = emptyList()), AndroidMailBehaviorStore(context).read())
    }

    @Test
    fun eachPlacesActionBarAndTheModeSurviveReopeningTheStore() {
        val context = RuntimeEnvironment.getApplication()
        val saved =
            MailBehavior(readLater = true, actionBarMode = ActionBarMode.Fixed).copy(
                actionBar =
                    mapOf(
                        ActionBarPlace.Inbox to ActionBarLayout(listOf(MailAction.Filters), MailAction.Search),
                        ActionBarPlace.Message to ActionBarLayout(listOf(MailAction.Spam), MailAction.ReadLater),
                        ActionBarPlace.Selection to ActionBarLayout(emptyList(), MailAction.Read),
                        ActionBarPlace.Compose to ActionBarLayout(listOf(MailAction.Discard), MailAction.Send),
                        ActionBarPlace.Everywhere to ActionBarLayout(listOf(MailAction.Reply), MailAction.Trash),
                    ),
            )
        AndroidMailBehaviorStore(context).write(saved)
        assertEquals(saved, AndroidMailBehaviorStore(context).read())
    }
}
