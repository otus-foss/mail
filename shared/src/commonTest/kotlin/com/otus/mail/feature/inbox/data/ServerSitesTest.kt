package com.otus.mail.feature.inbox.data

import com.otus.avatar.AvatarDomainResolver
import com.otus.jmap.login.JmapLogin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerSitesTest {
    private val ada = JmapLogin("ada", "https://mail.example.co.uk/jmap/session", "ada@example.co.uk", "Ada")

    @Test
    fun onlyALoadFindsTheDomainAndTheCachedListReadsItAfterwards() =
        runTest {
            var lookups = 0
            val sites =
                ServerSites {
                    lookups++
                    "example.co.uk"
                }
            assertEquals("", sites.known("ada"), "Nothing is trusted before a load")
            assertEquals("example.co.uk", sites.find(ada))
            assertEquals("example.co.uk", sites.find(ada))
            assertEquals("example.co.uk", sites.known("ada"))
            assertEquals(1, lookups)
        }

    @Test
    fun aFailedOrEmptyLookupFallsBackToTheWholeHost() =
        runTest {
            assertEquals("mail.example.co.uk", ServerSites { error("No suffix list") }.find(ada))
            assertEquals("mail.example.co.uk", ServerSites(AvatarDomainResolver { null }).find(ada))
        }
}
