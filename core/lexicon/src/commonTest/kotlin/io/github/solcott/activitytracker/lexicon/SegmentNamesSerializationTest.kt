package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentNameAdoption
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameViewerVote
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.SegmentViewerState
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `feed.segmentNameProposal`, `feed.segmentNameVote` and the naming parts of `#segmentView` as `AtJson` writes and reads them. */
class SegmentNamesSerializationTest {
    private val alice = Did("did:plc:alice")
    private val segment = AtUri.of(Did("did:plc:importer"), Nsid.FEED_SEGMENT, "osm-123")
    private val proposal = AtUri.of(alice, Nsid.FEED_SEGMENT_NAME_PROPOSAL, "3l2abcdefgh22")

    @Test
    fun proposalRoundTrips() {
        val record = SegmentNameProposalRecord(segment = segment, name = "Wiggly Woods", createdAt = Instant.parse("2026-10-28T12:00:00Z"))
        val json = AtJson.encodeToJsonElement(SegmentNameProposalRecord.serializer(), record).jsonObject
        assertEquals(Nsid.FEED_SEGMENT_NAME_PROPOSAL, json["\$type"]?.jsonPrimitive?.content)
        assertEquals(segment.toString(), json["segment"]?.jsonPrimitive?.content)
        assertEquals(record, AtJson.decodeFromJsonElement(SegmentNameProposalRecord.serializer(), json))
        val later =
            AtJson.decodeFromString(
                SegmentNameProposalRecord.serializer(),
                """{"${'$'}type":"${Nsid.FEED_SEGMENT_NAME_PROPOSAL}","segment":"$segment",""" +
                    """"name":"X","createdAt":"2026-10-28T12:00:00Z","x":1}""",
            )
        assertEquals("X", later.name)
    }

    @Test
    fun voteRoundTripsAndKeepsUnknownDirections() {
        val record =
            SegmentNameVoteRecord(
                subject = StrongRef(uri = proposal, cid = Cid("bafyproposal")),
                direction = SegmentNameVoteDirection.UP,
                createdAt = Instant.parse("2026-10-28T12:00:00Z"),
            )
        val json = AtJson.encodeToJsonElement(SegmentNameVoteRecord.serializer(), record).jsonObject
        assertEquals(Nsid.FEED_SEGMENT_NAME_VOTE, json["\$type"]?.jsonPrimitive?.content)
        assertEquals(record, AtJson.decodeFromJsonElement(SegmentNameVoteRecord.serializer(), json))
        val sideways = record.copy(direction = "sideways")
        assertEquals(
            "sideways",
            AtJson.decodeFromString(SegmentNameVoteRecord.serializer(), AtJson.encodeToString(SegmentNameVoteRecord.serializer(), sideways))
                .direction,
        )
    }

    @Test
    fun onlyProposalsComeFromTheFirehose() {
        assertTrue(Nsid.FEED_SEGMENT_NAME_PROPOSAL in Nsid.indexedCollections)
        assertFalse(Nsid.FEED_SEGMENT_NAME_VOTE in Nsid.indexedCollections)
    }

    @Test
    fun segmentViewWithNames() {
        val proposer = ProfileViewBasic(did = alice, handle = Handle("alice.test"))
        val view =
            segmentView()
                .copy(
                    name = "Wiggly Woods",
                    nameHistory =
                        listOf(
                            SegmentNameAdoption(
                                "Wiggly Woods",
                                Instant.parse("2026-09-01T00:00:00Z"),
                                up = 6,
                                down = 1,
                                proposer = proposer,
                            )
                        ),
                    nameProposals =
                        listOf(
                            SegmentNameProposalView(
                                uri = proposal,
                                cid = Cid("bafyproposal"),
                                name = "Rooty Run",
                                proposer = proposer,
                                openedAt = Instant.parse("2026-10-20T00:00:00Z"),
                                up = 2,
                                down = 0,
                                viewerVote =
                                    SegmentNameViewerVote(
                                        uri = AtUri.of(Did("did:plc:bob"), Nsid.FEED_SEGMENT_NAME_VOTE, "3l2abcdefgh33"),
                                        direction = SegmentNameVoteDirection.UP,
                                    ),
                            )
                        ),
                    viewer = SegmentViewerState(canProposeName = true, canVoteOnNames = true),
                )
        val json = AtJson.encodeToJsonElement(SegmentView.serializer(), view).jsonObject
        assertEquals(view, AtJson.decodeFromJsonElement(SegmentView.serializer(), json))
        assertEquals("Wiggly Woods", view.currentName)
        assertEquals("Trail 214 Connector", view.record.name)
    }

    @Test
    fun segmentViewWithoutNameFallsBackToTheRecord() {
        val json = AtJson.encodeToJsonElement(SegmentView.serializer(), segmentView()).jsonObject.toMutableMap()
        json.remove("name")
        json.remove("nameHistory")
        json.remove("nameProposals")
        val view = AtJson.decodeFromJsonElement(SegmentView.serializer(), kotlinx.serialization.json.JsonObject(json))
        assertNull(view.name)
        assertEquals("Trail 214 Connector", view.currentName)
        assertTrue(view.nameHistory.isEmpty())
        assertTrue(view.nameProposals.isEmpty())
    }

    private fun segmentView() =
        SegmentView(
            uri = segment,
            cid = Cid("bafysegment"),
            creator = ProfileViewBasic(did = Did("did:plc:importer"), handle = Handle("importer.test")),
            record =
                SegmentRecord(
                    name = "Trail 214 Connector",
                    sport = "ride",
                    line = "_p~iF~ps|U_ulLnnqC",
                    source = SegmentSource.OSM,
                    license = "ODbL-1.0",
                    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                ),
            distanceMeters = 1200,
        )
}
