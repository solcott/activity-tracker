package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.BlockRecord
import io.github.solcott.activitytracker.lexicon.app.BlockSource
import io.github.solcott.activitytracker.lexicon.app.BskyBlockRecord
import io.github.solcott.activitytracker.lexicon.app.GetBlocks
import io.github.solcott.activitytracker.lexicon.app.MuteRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileViewerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `graph.block`, `graph.mute`, Bluesky's `app.bsky.graph.block` as read, and the views that report them. */
class BlockRecordsTest {
    @Test
    fun blockAndMuteRoundTripKeyedBySubject() {
        val bob = Did("did:plc:bob")
        val at = Instant.parse("2026-10-21T09:00:00Z")
        val block = BlockRecord(bob, at)
        val blockJson = AtJson.encodeToJsonElement(BlockRecord.serializer(), block).jsonObject
        assertEquals(Nsid.GRAPH_BLOCK, blockJson["\$type"]?.jsonPrimitive?.content)
        assertEquals(block, AtJson.decodeFromJsonElement(BlockRecord.serializer(), blockJson))
        assertEquals("did:plc:bob", BlockRecord.rkey(bob))

        val mute = MuteRecord(bob, at)
        val muteJson = AtJson.encodeToJsonElement(MuteRecord.serializer(), mute).jsonObject
        assertEquals(Nsid.GRAPH_MUTE, muteJson["\$type"]?.jsonPrimitive?.content)
        assertEquals(mute, AtJson.decodeFromJsonElement(MuteRecord.serializer(), muteJson))
        assertEquals("did:plc:bob", MuteRecord.rkey(bob))
    }

    @Test
    fun bskyBlockDecodesFromBlueskyJson() {
        val json = """{"${'$'}type":"app.bsky.graph.block","subject":"did:plc:bob","createdAt":"2025-01-02T03:04:05.000Z","via":"x"}"""
        val block = AtJson.decodeFromString(BskyBlockRecord.serializer(), json)
        assertEquals(Did("did:plc:bob"), block.subject)
        assertTrue(Nsid.BSKY_GRAPH_BLOCK in Nsid.indexedCollections)
    }

    @Test
    fun blockViewCarriesSources() {
        val json =
            """{"blocks":[{"actor":{"did":"did:plc:bob","handle":"bob.test"},"sources":["app","bluesky"],"createdAt":"2026-10-21T09:00:00Z"}]}"""
        val out = AtJson.decodeFromString(GetBlocks.Output.serializer(), json)
        assertEquals(listOf(BlockSource.APP, BlockSource.BLUESKY), out.blocks.single().sources)
    }

    @Test
    fun viewerStateBlockingAndMutingAreOptional() {
        val empty = AtJson.encodeToJsonElement(ProfileViewerState.serializer(), ProfileViewerState()).jsonObject
        assertFalse("blocking" in empty)
        assertFalse("muting" in empty)
    }
}
