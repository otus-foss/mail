package com.otusfoss.mail.feature.folders.data

import com.otusfoss.mail.core.cache.AccountMailCache
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.feature.folders.domain.AccountFolderRules
import com.otusfoss.mail.feature.folders.domain.AddressField
import com.otusfoss.mail.feature.folders.domain.AddressMatch
import com.otusfoss.mail.feature.folders.domain.FolderRule
import com.otusfoss.mail.feature.folders.domain.RuleAvailability
import com.otusfoss.mail.feature.folders.domain.RuleCondition
import com.otusfoss.mail.feature.folders.domain.RuleFailure
import com.otusfoss.mail.feature.folders.domain.RuleSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeptFolderRulesTest {
    private val cache = AccountMailCache(InMemoryMailCache())
    private val kept = KeptFolderRules(cache)

    private val loaded =
        AccountFolderRules(
            "ada",
            "Ada",
            RuleAvailability.Available(RuleSupport(setOf("fileinto", "mailboxid"))),
            mapOf(
                "P" to
                    listOf(
                        FolderRule(RuleCondition.Address(AddressField.From, AddressMatch.Exact("jana@agency.example"))),
                    ),
            ),
        )
    private val offline =
        AccountFolderRules("ada", "Ada", RuleAvailability.Failed(RuleFailure.Mail(MailFailure.Unreachable)))

    @Test
    fun anUnreachableServerShowsTheRulesLastLoaded() =
        runTest {
            kept.load("ada") { loaded }

            assertEquals(loaded.rules, kept.load("ada") { offline }.rules)
        }

    @Test
    fun rulesLoadedWhileTheLoginReconnectsAreNotKept() =
        runTest {
            assertFailsWith<CancellationException> {
                kept.load("ada") { loaded.also { cache.connected("ada") } }
            }

            assertEquals(offline, kept.load("ada") { offline })
        }
}
