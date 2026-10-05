package com.otus.jmap.login

import com.otus.jmap.JmapAuth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JmapLoginTest {
    @Test
    fun secretsAreNeverPrinted() {
        assertTrue("hunter2" !in JmapAuth.Basic("ada", "hunter2").toString())
        assertTrue("s3cr3t-t0k3n" !in JmapAuth.Bearer("s3cr3t-t0k3n").toString())
        assertTrue("hunter2" !in LoginSecret.Found("hunter2").toString())
    }

    @Test
    fun basicAuthFollowsRfc7617() {
        assertEquals("Basic YWRhOmh1bnRlcjI=", JmapAuth.Basic("ada", "hunter2").header())
        assertFailsWith<IllegalArgumentException> { JmapAuth.Basic("a:da", "x") }
    }

    @Test
    fun aLoginMakesTheAuthItWasSavedWith() {
        val login = JmapLogin("1", "https://a.example/s", "ada", "Ada")
        assertIs<JmapAuth.Basic>(login.auth("x"))
        assertIs<JmapAuth.Bearer>(login.copy(method = AuthMethod.Bearer).auth("x"))
    }

    @Test
    fun unreadableListsAreReportedInsteadOfEmpty() {
        assertIs<LoginCodec.Decoded.Unreadable>(LoginCodec.decode("{not json"))
        assertEquals(emptyList(), (LoginCodec.decode(null) as LoginCodec.Decoded.Logins).logins)
    }

    @Test
    fun earlierAccountRecordsStillDecode() {
        val earlier = """[{"id": "x", "sessionUrl": "https://a.example/.well-known/jmap", "username": "ada",
            "jmapAccountId": "d1", "name": "Ada"}]"""
        val login = (LoginCodec.decode(earlier) as LoginCodec.Decoded.Logins).logins.single()
        assertEquals(JmapLogin("x", "https://a.example/.well-known/jmap", "ada", "Ada"), login)
    }

    @Test
    fun theInMemoryStoreReplacesAndRemoves() {
        val store = InMemoryLoginStore()
        val login = JmapLogin("1", "https://a.example/s", "ada", "Ada")
        store.save(login, "one")
        store.save(login.copy(name = "Ada L."), "two")
        assertEquals("Ada L.", store.logins().single().name)
        assertEquals("two", (store.secret("1") as LoginSecret.Found).value)
        store.remove("1")
        assertIs<LoginSecret.Missing>(store.secret("1"))
    }
}
