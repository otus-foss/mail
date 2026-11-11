package com.otusfoss.mail.feature.notifications.domain

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.PushKeys
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.core.jmap.push.PushEndpoint
import com.otusfoss.core.storage.StringMapStore
import com.otusfoss.mail.app.MailServices
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class MailPushTest {
    private class States : StringMapStore {
        var records = emptyMap<String, String>()

        override fun read() = records

        override fun write(values: Map<String, String>) {
            records = values
        }
    }

    @Test
    fun iosAsksOnlyForNewMailAndAndroidForEveryChange() =
        runTest {
            assertEquals("""["EmailDelivery"]""", typesSent(MailPush.Deliveries), "Each iOS push shows an alert")
            assertEquals("""["EmailDelivery","Email"]""", typesSent(MailPush.Changes))
        }

    /** The `types` a new subscription for [push] asks the server for. */
    private suspend fun typesSent(push: MailPush): String {
        val sent = mutableListOf<String>()
        val logins = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
        val engine =
            stalwartLike { body ->
                sent += body
                if ("PushSubscription/get" in body) {
                    """{"methodResponses": [["PushSubscription/get", {"list": [], "notFound": []}, "c0"]]}"""
                } else {
                    """{"methodResponses": [["PushSubscription/set", {"created": {"push": {"id": "ps1"}}}, "c0"]]}"""
                }
            }
        val subscriptions =
            MailServices(
                logins,
                createClient = { JmapClient(engine) },
            ).pushSubscriptions(States(), "d", push)
        subscriptions.setEndpoint(ADA_LOGIN.id, PushEndpoint("https://push.example/1", PushKeys("BKey", "c2Vj")))
        subscriptions.sync()
        return Json
            .parseToJsonElement(sent.single { "PushSubscription/set" in it })
            .jsonObject
            .getValue("methodCalls")
            .jsonArray
            .single()
            .jsonArray[1]
            .jsonObject
            .getValue("create")
            .jsonObject
            .values
            .single()
            .jsonObject
            .getValue("types")
            .toString()
    }
}
