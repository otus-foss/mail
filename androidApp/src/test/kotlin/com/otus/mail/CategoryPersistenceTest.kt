package com.otus.mail

import android.content.Context
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.category.InboxCategories
import com.otus.mail.feature.inbox.domain.category.MailCategory
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

    @Test
    fun categoriesStartOffWithSocialHidden() {
        context
            .getSharedPreferences("otus_mail_behavior", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean("read_later", true)
            .apply()
        assertEquals(InboxCategories(), AndroidMailBehaviorStore(context).read().categories)
    }

    @Test
    fun categoriesAndSenderChoicesSurviveReopeningTheStore() {
        val saved =
            MailBehavior(
                categories =
                    InboxCategories(
                        enabled = true,
                        hidden = setOf(MailCategory.Updates),
                        senders = mapOf("news@shop.example" to MailCategory.Primary),
                    ),
            )
        AndroidMailBehaviorStore(context).write(saved)
        assertEquals(saved, AndroidMailBehaviorStore(context).read())
    }

    @Test
    fun showingEveryCategoryIsKept() {
        val saved = MailBehavior(categories = InboxCategories(enabled = true, hidden = emptySet()))
        AndroidMailBehaviorStore(context).write(saved)
        assertEquals(emptySet(), AndroidMailBehaviorStore(context).read().categories.hidden)
    }
}
