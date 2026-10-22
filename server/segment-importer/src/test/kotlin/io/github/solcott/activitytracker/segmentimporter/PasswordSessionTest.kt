package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.lexicon.atproto.GetRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class PasswordSessionTest {
    private val fake = FakeServices()

    private fun client(password: String = FakeServices.PASSWORD): XrpcClient =
        XrpcClient(fake.http, FakeServices.PDS, PasswordSession(fake.http, FakeServices.PDS, FakeServices.ACCOUNT, password))

    private suspend fun XrpcClient.get() = query(GetRecord.method, GetRecord.Params(FakeServices.DID, "c", "k"))

    init {
        fake.on("com.atproto.repo.getRecord") { json("""{"uri":"at://${FakeServices.DID}/c/k","value":{}}""") }
    }

    @Test
    fun signsInOnceAndReusesTheSession() = runTest {
        val pds = client()
        pds.get()
        pds.get()
        assertEquals(1, fake.sessions)
        assertEquals(listOf("Bearer access-1", "Bearer access-1"), fake.calls("com.atproto.repo.getRecord").map { it.auth })
    }

    @Test
    fun anExpiredTokenIsRefreshed() = runTest {
        val pds = client()
        pds.get()
        fake.validAccess = "access-2"
        pds.get()
        assertEquals(1 to 1, fake.sessions to fake.refreshes)
        assertEquals("Bearer access-r1", fake.calls("com.atproto.repo.getRecord").last().auth)
    }

    @Test
    fun aRefusedRefreshSignsInAgain() = runTest {
        val pds = client()
        pds.get()
        fake.validAccess = "access-3"
        fake.refreshAllowed = false
        fake.calls.clear()
        pds.get()
        assertEquals(
            listOf(
                "com.atproto.repo.getRecord",
                "com.atproto.server.refreshSession",
                "com.atproto.server.createSession",
                "com.atproto.repo.getRecord",
            ),
            fake.calls.map { it.nsid },
        )
        assertEquals("Bearer access-3", fake.calls.last().auth)
    }

    @Test
    fun aRefusedPasswordFails() = runTest {
        val failed = assertFailsWith<PasswordSession.SignInFailed> { client(password = "wrong").get() }
        assertEquals(true, "importer.test" in failed.message.orEmpty())
        assertEquals(0, fake.calls("com.atproto.repo.getRecord").size)
    }
}
