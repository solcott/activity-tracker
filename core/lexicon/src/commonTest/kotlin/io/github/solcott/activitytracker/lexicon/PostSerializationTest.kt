package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.PostEmbed
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostProposalStatus
import io.github.solcott.activitytracker.lexicon.app.PostRecord
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.PostViewerState
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.QuotedPost
import io.github.solcott.activitytracker.lexicon.app.QuotedPostEmbed
import io.github.solcott.activitytracker.lexicon.app.ReasonRepost
import io.github.solcott.activitytracker.lexicon.app.RepostRecord
import io.github.solcott.activitytracker.lexicon.app.RepostedPostView
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
            """{"${'$'}type":"${Nsid.FEED_POST}","embed":{"${'$'}type":"${Nsid.FEED_POST}#video","video":{}},"createdAt":"2026-11-06T12:00:00Z"}"""
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
        val embed = AtJson.parseToJsonElement("""{"${'$'}type":"${Nsid.FEED_DEFS}#postVideoEmbedView"}""")
        val changed = kotlinx.serialization.json.JsonObject(json + ("embed" to embed))
        assertIs<PostEmbedView.Unknown>(AtJson.decodeFromJsonElement(PostView.serializer(), changed).embed)
    }

    @Test
    fun viewFromAnOlderAppViewHasNoCounts() {
        val json = AtJson.encodeToJsonElement(PostView.serializer(), view).jsonObject
        val older = kotlinx.serialization.json.JsonObject(json - "likeCount" - "commentCount" - "repostCount" - "quoteCount" - "viewer")
        val decoded = AtJson.decodeFromJsonElement(PostView.serializer(), older)
        assertEquals(0, decoded.likeCount)
        assertEquals(0, decoded.commentCount)
        assertEquals(0, decoded.repostCount)
        assertEquals(0, decoded.quoteCount)
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

    @Test
    fun viewerRepostRoundTripsAndAnOlderViewerHasNone() {
        val repost = AtUri("at://did:plc:bob/${Nsid.FEED_REPOST}/3krepost")
        val counted = view.copy(repostCount = 1, viewer = PostViewerState(repost = repost))
        val json = AtJson.encodeToJsonElement(PostView.serializer(), counted).jsonObject
        assertEquals(repost.value, json.getValue("viewer").jsonObject.getValue("repost").jsonPrimitive.content)
        assertEquals(counted, AtJson.decodeFromJsonElement(PostView.serializer(), json))
        val olderViewer = """{"like":"at://did:plc:bob/${Nsid.FEED_LIKE}/3klike"}"""
        assertEquals(null, AtJson.decodeFromString(PostViewerState.serializer(), olderViewer).repost)
    }

    @Test
    fun repostRecordRoundTripsWithItsType() {
        val record = RepostRecord(StrongRef(view.uri, view.cid), Instant.parse("2026-11-07T09:00:00Z"))
        val json = AtJson.encodeToJsonElement(RepostRecord.serializer(), record).jsonObject
        assertEquals(Nsid.FEED_REPOST, json["\$type"]?.jsonPrimitive?.content)
        assertEquals(record, AtJson.decodeFromJsonElement(RepostRecord.serializer(), json))
    }

    @Test
    fun timelineCarriesRepostedPostsBesideOlderItems() {
        val reposted =
            RepostedPostView(
                post = view,
                reason =
                    ReasonRepost(
                        ProfileViewBasic(Did("did:plc:bob"), Handle("bob.test")),
                        Instant.parse("2026-11-07T09:00:00Z"),
                        others = 2,
                    ),
            )
        val output = GetTimeline.Output(listOf(TimelineItem.Repost(reposted), TimelineItem.Post(view)))
        val json = AtJson.encodeToJsonElement(GetTimeline.Output.serializer(), output).jsonObject
        val first = (json.getValue("feed") as kotlinx.serialization.json.JsonArray)[0].jsonObject
        assertEquals(RepostedPostView.TYPE, first["\$type"]?.jsonPrimitive?.content)
        assertEquals(output, AtJson.decodeFromJsonElement(GetTimeline.Output.serializer(), json))
    }

    @Test
    fun reasonWithoutOthersDecodesAsNone() {
        val raw = """{"by":{"did":"did:plc:bob","handle":"bob.test"},"createdAt":"2026-11-07T09:00:00Z"}"""
        assertEquals(0, AtJson.decodeFromString(ReasonRepost.serializer(), raw).others)
    }

    private val quoteUri = AtUri("at://did:plc:bob/${Nsid.FEED_POST}/3kquote")

    private fun quotedPost(embed: QuotedPostEmbed?) =
        QuotedPost.Post(
            uri = view.uri,
            cid = view.cid,
            author = view.author,
            createdAt = view.createdAt,
            text = view.text,
            visibility = "followers",
            embed = embed,
        )

    @Test
    fun quoteRecordRoundTrips() {
        val quote = PostRecord(PostEmbed.Quote(StrongRef(view.uri, view.cid)), Instant.parse("2026-11-07T10:00:00Z"), text = "Yes, and…")
        val json = AtJson.encodeToJsonElement(PostRecord.serializer(), quote).jsonObject
        val embed = json.getValue("embed").jsonObject
        assertEquals(PostEmbed.Quote.TYPE, embed["\$type"]?.jsonPrimitive?.content)
        assertEquals(view.uri.value, embed.getValue("subject").jsonObject.getValue("uri").jsonPrimitive.content)
        assertEquals(quote, AtJson.decodeFromJsonElement(PostRecord.serializer(), json))
    }

    @Test
    fun eachQuotedMemberRoundTrips() {
        val proposalEmbed = view.embed as PostEmbedView.Proposal
        val members =
            listOf(
                QuotedPost.Unavailable(view.uri),
                quotedPost(proposalEmbed),
                quotedPost(QuotedPostEmbed.Link(AtUri("at://did:plc:carol/${Nsid.FEED_POST}/3kfirst"))),
                quotedPost(null),
            )
        for (quoted in members) {
            val quote = view.copy(uri = quoteUri, embed = PostEmbedView.Quote(quoted), quoteCount = 2)
            val json = AtJson.encodeToJsonElement(PostView.serializer(), quote).jsonObject
            assertEquals(PostEmbedView.Quote.TYPE, json.getValue("embed").jsonObject["\$type"]?.jsonPrimitive?.content)
            assertEquals(quote, AtJson.decodeFromJsonElement(PostView.serializer(), json))
        }
    }

    @Test
    fun unknownQuotedMembersDecodeAndReEncodeUnchanged() {
        val quoted = """{"${'$'}type":"${Nsid.FEED_DEFS}#quotedPostBlocked","uri":"${view.uri.value}"}"""
        val embed = AtJson.parseToJsonElement("""{"${'$'}type":"${PostEmbedView.Quote.TYPE}","quoted":$quoted}""")
        val decoded = AtJson.decodeFromJsonElement(PostEmbedView.serializer(), embed)
        assertIs<QuotedPost.Unknown>(assertIs<PostEmbedView.Quote>(decoded).quoted)
        assertEquals(embed, AtJson.encodeToJsonElement(PostEmbedView.serializer(), decoded))

        val inner = AtJson.encodeToJsonElement(QuotedPost.serializer(), quotedPost(null)).jsonObject
        val video = AtJson.parseToJsonElement("""{"${'$'}type":"${Nsid.FEED_DEFS}#postVideoEmbedView"}""")
        val withVideo = kotlinx.serialization.json.JsonObject(inner + ("embed" to video))
        val decodedPost = assertIs<QuotedPost.Post>(AtJson.decodeFromJsonElement(QuotedPost.serializer(), withVideo))
        assertIs<QuotedPostEmbed.Unknown>(decodedPost.embed)
        assertEquals(withVideo, AtJson.encodeToJsonElement(QuotedPost.serializer(), decodedPost))
    }
}
