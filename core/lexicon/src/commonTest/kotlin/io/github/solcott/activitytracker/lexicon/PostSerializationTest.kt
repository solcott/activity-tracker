package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.PostEmbed
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostProposalStatus
import io.github.solcott.activitytracker.lexicon.app.PostRecord
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.PostViewerState
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentViewBasic
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PostSerializationTest {
    private val proposal = StrongRef(AtUri("at://did:plc:alice/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3kabc"), Cid("bafyreip"))
    private val record =
        PostRecord(
            embed = PostEmbed.Proposal(proposal.uri),
            createdAt = Instant.parse("2026-11-06T12:00:00Z"),
            text = "Everyone already calls it this",
        )
    private val view =
        PostView(
            uri = AtUri("at://did:plc:alice/${Nsid.FEED_POST}/3kabc"),
            cid = Cid("bafyreiq"),
            author = ProfileViewBasic(Did("did:plc:alice"), Handle("alice.test")),
            embed =
                PostEmbedView.Proposal(
                    proposal = proposal,
                    name = "Wiggly Woods",
                    status = PostProposalStatus.OPEN,
                    openedAt = Instant.parse("2026-11-06T12:00:00Z"),
                    up = 3,
                    down = 1,
                    segment = SegmentViewBasic(AtUri("at://did:plc:bob/${Nsid.FEED_SEGMENT}/3kseg"), "Trail 214", "ride", 1200),
                    canVote = true,
                ),
            createdAt = Instant.parse("2026-11-06T12:00:00Z"),
            indexedAt = Instant.parse("2026-11-06T12:00:01Z"),
            text = "Everyone already calls it this",
            visibility = "followers",
        )

    @Test
    fun recordRoundTripsWithTypes() {
        val json = AtJson.encodeToJsonElement(PostRecord.serializer(), record).jsonObject
        assertEquals(Nsid.FEED_POST, json["\$type"]?.jsonPrimitive?.content)
        assertEquals(PostEmbed.Proposal.TYPE, json["embed"]?.jsonObject?.get("\$type")?.jsonPrimitive?.content)
        assertEquals(record, AtJson.decodeFromJsonElement(PostRecord.serializer(), json))
    }

    @Test
    fun noTextIsOmitted() {
        val json = AtJson.encodeToJsonElement(PostRecord.serializer(), record.copy(text = null)).jsonObject
        assertFalse("text" in json)
    }

    @Test
    fun unknownEmbedRoundTrips() {
        val raw =
            """{"${'$'}type":"${Nsid.FEED_POST}","embed":{"${'$'}type":"${Nsid.FEED_POST}#quote","post":{}},"createdAt":"2026-11-06T12:00:00Z"}"""
        val decoded = AtJson.decodeFromString(PostRecord.serializer(), raw)
        assertIs<PostEmbed.Unknown>(decoded.embed)
        assertEquals(AtJson.parseToJsonElement(raw), AtJson.encodeToJsonElement(PostRecord.serializer(), decoded))
    }

    @Test
    fun timelineCarriesPostsAndSkipsUnknown() {
        val output =
            GetTimeline.Output(
                listOf(TimelineItem.Post(view), TimelineItem.Unknown(AtJson.parseToJsonElement("""{"${'$'}type":"x#y"}""").jsonObject))
            )
        val json = AtJson.encodeToString(GetTimeline.Output.serializer(), output)
        val decoded = AtJson.decodeFromString(GetTimeline.Output.serializer(), json)
        assertEquals(listOf(view), decoded.posts)
        assertIs<TimelineItem.Unknown>(decoded.feed[1])
    }

    @Test
    fun unknownEmbedViewDecodes() {
        val json = AtJson.encodeToJsonElement(PostView.serializer(), view).jsonObject
        val embed = AtJson.parseToJsonElement("""{"${'$'}type":"${Nsid.FEED_DEFS}#postQuoteEmbedView"}""")
        val changed = kotlinx.serialization.json.JsonObject(json + ("embed" to embed))
        assertIs<PostEmbedView.Unknown>(AtJson.decodeFromJsonElement(PostView.serializer(), changed).embed)
    }

    @Test
    fun viewFromAnOlderAppViewHasNoCounts() {
        val json = AtJson.encodeToJsonElement(PostView.serializer(), view).jsonObject
        val older = kotlinx.serialization.json.JsonObject(json - "likeCount" - "commentCount" - "viewer")
        val decoded = AtJson.decodeFromJsonElement(PostView.serializer(), older)
        assertEquals(0, decoded.likeCount)
        assertEquals(0, decoded.commentCount)
        assertEquals(null, decoded.viewer)
    }

    @Test
    fun countsAndViewerLikeRoundTrip() {
        val like = AtUri("at://did:plc:bob/${Nsid.FEED_LIKE}/3klike")
        val counted = view.copy(likeCount = 2, commentCount = 3, viewer = PostViewerState(like))
        val json = AtJson.encodeToJsonElement(PostView.serializer(), counted).jsonObject
        assertEquals(like.value, json.getValue("viewer").jsonObject.getValue("like").jsonPrimitive.content)
        assertEquals(counted, AtJson.decodeFromJsonElement(PostView.serializer(), json))
    }
}
