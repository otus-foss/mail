package com.otus.mail.feature.folders.presentation

import com.otus.avatar.AvatarImage
import com.otus.avatar.AvatarImageLoader
import com.otus.avatar.AvatarService
import com.otus.mail.core.avatar.mailAvatarOptions
import com.otus.mail.feature.folders.domain.AccountFolderRules
import com.otus.mail.feature.folders.domain.AddressField
import com.otus.mail.feature.folders.domain.AddressMatch
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.RuleAvailability
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.RuleSupport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class FolderSendersTest {
    private val fromAda = RuleCondition.Address(AddressField.From, AddressMatch.Exact("ada@example.org"))
    private val rules =
        AccountFolderRules(
            "a",
            "Work",
            RuleAvailability.Available(RuleSupport(emptySet())),
            mapOf("f" to listOf(FolderRule(fromAda))),
        )

    @Test
    fun sendersResolveAgainWithoutTheWebsiteIconsFoundBeforeTurningThemOff() =
        runTest {
            val icon = AvatarImage(byteArrayOf(1))
            val urls = mutableListOf<String>()
            var websiteIcons = true
            val avatars =
                AvatarService(
                    images =
                        AvatarImageLoader {
                            urls += it
                            icon
                        },
                )
            val senders = FolderSenders(avatars, { mailAvatarOptions(websiteIcons) }) { null }
            senders.resolve(listOf(rules))
            assertSame(icon, senders.ada.avatar?.image)

            websiteIcons = false
            urls.clear()
            assertNull(senders.ada.avatar, "The old icon is not shown")
            senders.resolve(listOf(rules))
            assertNotNull(senders.ada.avatar)
            assertNull(senders.ada.avatar?.image)
            assertEquals(emptyList<String>(), urls, "No website is asked")
        }

    private val FolderSenders.ada: FolderSender
        get() = of("a", listOf("ada@example.org")).single()
}
