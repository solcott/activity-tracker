package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class JetstreamEventTest {
    @Test
    fun decodesCommitMessage() {
        val event =
            JetstreamEvent.decodeFrame(
                """
                {"${'$'}type":"message","payload":{"${'$'}type":"network.bsky.jetstream.subscribeEvents#commit","seq":12345,
                 "did":"did:plc:alice","time":"2026-09-30T19:46:02.329308Z","witnessedAt":"2026-09-30T19:46:02.329309Z",
                 "rev":"3l3qo2vutsw2b","operation":"create","collection":"app.activitytracker.feed.activity","rkey":"3kabc",
                 "cid":"bafyabc","record":{"${'$'}type":"app.activitytracker.feed.activity","sport":"run"}}}
                """
            )
        val commit = assertIs<JetstreamEvent.Commit>(event)
        assertEquals("did:plc:alice", commit.did)
        assertEquals("create", commit.operation)
        assertEquals("3kabc", commit.rkey)
        assertEquals("bafyabc", commit.cid)
        assertEquals(1_790_797_562_329_309L, commit.witnessedAtUs)
    }

    @Test
    fun cursorFallsBackToTimeWithoutWitnessedAt() {
        val event =
            JetstreamEvent.decodeFrame(
                """
                {"${'$'}type":"message","payload":{"${'$'}type":"network.bsky.jetstream.subscribeEvents#account","seq":7,
                 "did":"did:plc:alice","time":"2026-09-30T19:46:02.000001Z",
                 "account":{"seq":99,"did":"did:plc:alice","time":"2026-09-30T19:46:01Z","active":false,"status":"deleted"}}}
                """
            )
        val account = assertIs<JetstreamEvent.Account>(event)
        assertEquals("deleted", account.account.status)
        assertEquals(1_790_797_562_000_001L, account.witnessedAtUs)
    }

    @Test
    fun skipsSyncAndInfo() {
        assertNull(
            JetstreamEvent.decodeFrame(
                """
                {"${'$'}type":"message","payload":{"${'$'}type":"network.bsky.jetstream.subscribeEvents#info",
                 "name":"OutdatedCursor"}}
                """
            )
        )
        assertNull(
            JetstreamEvent.decodeFrame(
                """
                {"${'$'}type":"message","payload":{"${'$'}type":"network.bsky.jetstream.subscribeEvents#sync",
                 "seq":1,"did":"did:plc:a","time":"2026-09-30T00:00:00Z","sync":{}}}
                """
            )
        )
    }

    @Test
    fun errorFrameIsTerminal() {
        assertFailsWith<JetstreamEvent.StreamError> {
            JetstreamEvent.decodeFrame("""{"${'$'}type":"error","error":"ConsumerTooSlow","message":"too slow"}""")
        }
    }
}
