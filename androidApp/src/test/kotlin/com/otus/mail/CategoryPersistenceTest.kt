package com.otus.mail

import android.content.Context
import com.otus.mail.core.categories.domain.InboxCategories
import com.otus.mail.core.categories.domain.MailCategory
import com.otus.mail.core.settings.domain.ListBehavior
import com.otus.mail.core.settings.domain.MailBehavior
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CategoryPersistenceTest {
    private val context = RuntimeEnvironment.getApplication()
    private val preferences = context.getSharedPreferences("otus_mail_behavior", Context.MODE_PRIVATE)

    @Test
    fun categoriesStartOffWithEveryCategoryShown() {
        preferences
            .edit()
            .clear()
            .putBoolean("read_later", true)
            .apply()
        assertEquals(InboxCategories(), AndroidMailBehaviorStore(context).read().list.categories)
    }

    @Test
    fun categoriesAndSenderChoicesSurviveReopeningTheStore() {
        val categories =
            InboxCategories(
                enabled = true,
                hidden = setOf(MailCategory.Notifications, MailCategory.Newsletters),
                senders = mapOf("news@shop.example" to MailCategory.Primary, "a@b.example" to MailCategory.Newsletters),
            )
        AndroidMailBehaviorStore(context).write(MailBehavior(list = ListBehavior(categories = categories)))
        assertEquals(categories, AndroidMailBehaviorStore(context).read().list.categories)
    }

    @Test
    fun showingEveryCategoryIsKept() {
        val categories = InboxCategories(enabled = true, hidden = emptySet())
        AndroidMailBehaviorStore(context).write(MailBehavior(list = ListBehavior(categories = categories)))
        assertEquals(
            emptySet<MailCategory>(),
            AndroidMailBehaviorStore(context)
                .read()
                .list.categories.hidden,
        )
    }

    @Test
    fun linesThisVersionDoesNotKnowAreLeftOut() {
        preferences
            .edit()
            .clear()
            .putString("hidden_inbox_categories", "notifications\nforums")
            .putString("sender_categories", "x@y.example\tnotifications\nbad\tforums\nnotab\n\tnewsletters")
            .apply()
        val categories = AndroidMailBehaviorStore(context).read().list.categories
        assertEquals(setOf(MailCategory.Notifications), categories.hidden)
        assertEquals(mapOf("x@y.example" to MailCategory.Notifications), categories.senders)
    }
}
