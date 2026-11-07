package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.AvatarUrls
import io.github.solcott.activitytracker.appview.api.LabelSources
import io.github.solcott.activitytracker.appview.api.MediaUrls
import io.github.solcott.activitytracker.appview.api.Views
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.labels.LabelStore
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetAuthorPosts
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.Visibility
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.update

/** Shared fixtures for post tests (feed/posts): an active segment, a regular who proposes a name, and sharing it at a tier. */
open class PostTestBase : SegmentTestBase() {
    private val labeler = "did:plc:labeler"
    protected val views = Views(db, AvatarUrls("https://appview.test"), MediaUrls("https://appview.test"))
    protected val seg = segmentUri("3ks", importer)
    protected val now = Clock.System.now()
    private var effortIds = 0

    @AfterTest fun forgetLabelers() = run { LabelSources.trusted = emptyList() }

    protected fun activeSegment() {
        segment("3ks", sport = "ride", did = importer, name = "Trail 214")
        runBlocking { db.tx { Segments.update({ Segments.uri eq seg }) { it[status] = SegmentStatus.ACTIVE } } }
    }

    /** [days] efforts by [did] on distinct dates, so they're a regular after 5. */
    protected fun efforts(did: String, days: Int) = runBlocking {
        db.tx {
            SegmentEfforts.batchInsert(0 until days) { i ->
                this[SegmentEfforts.id] = "effort-${effortIds++}"
                this[SegmentEfforts.segmentUri] = seg
                this[SegmentEfforts.activityUri] = activityUri("3ke$i", did)
                this[SegmentEfforts.trackUri] = trackUri("3ke$i", did)
                this[SegmentEfforts.authorDid] = did
                this[SegmentEfforts.sport] = "ride"
                this[SegmentEfforts.eBike] = false
                this[SegmentEfforts.startOffsetS] = i * 10L
                this[SegmentEfforts.elapsedS] = 600
                this[SegmentEfforts.visibility] = Visibility.SELF
                this[SegmentEfforts.startedOn] = LocalDate.of(2026, 9, 1).plusDays(i.toLong()).toString()
            }
        }
    }

    protected fun proposalUri(rkey: String = "3kp", did: String = alice) = "at://$did/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/$rkey"

    protected fun postUri(rkey: String = "3kp", did: String = alice) = "at://$did/${Nsid.FEED_POST}/$rkey"

    protected fun propose(name: String = "Wiggly Woods", rkey: String = "3kp", did: String = alice) =
        commit(did, Nsid.FEED_SEGMENT_NAME_PROPOSAL, rkey, """{"segment":"$seg","name":"$name","createdAt":"${now - 2.hours}"}""")

    /** [did] shares [proposal] at [tier], under [rkey]. */
    @Suppress("LongParameterList")
    protected fun share(
        tier: String = Visibility.PUBLIC,
        rkey: String = "3kp",
        did: String = alice,
        proposal: String = proposalUri(rkey, did),
        text: String? = "Everyone already calls it this",
        createdAt: kotlin.time.Instant = now - 1.hours,
    ) {
        val textField = text?.let { ""","text":"$it"""" }.orEmpty()
        put(
            did,
            Nsid.FEED_POST,
            rkey,
            """{"embed":{"${'$'}type":"${Nsid.FEED_POST}#proposal","proposal":"$proposal"},"createdAt":"$createdAt"$textField}""",
            tier,
        )
    }

    /** [did] quotes the post at [subject] at [tier], under [rkey] (feed/quote-posts); a null [text] writes none. */
    @Suppress("LongParameterList")
    protected fun quote(
        did: String,
        rkey: String,
        subject: String = postUri(),
        tier: String = Visibility.PUBLIC,
        text: String? = "Yes, and the lower half too",
        createdAt: kotlin.time.Instant = now - 30.minutes,
    ) {
        val textField = text?.let { ""","text":"$it"""" }.orEmpty()
        put(
            did,
            Nsid.FEED_POST,
            rkey,
            """{"embed":{"${'$'}type":"${Nsid.FEED_POST}#quote","subject":{"uri":"$subject","cid":"bafyquoted"}},""" +
                """"createdAt":"$createdAt"$textField}""",
            tier,
        )
    }

    /** Alice, a regular on the active segment, proposes "Wiggly Woods"; bob and carol are known accounts, bob a rider. */
    protected fun aliceProposes() {
        activeSegment()
        efforts(alice, 5)
        efforts(bob, 1)
        commit(bob, Nsid.ACTOR_PROFILE, "self", """{"displayName":"b"}""")
        commit(carol, Nsid.ACTOR_PROFILE, "self", """{"displayName":"c"}""")
        propose()
    }

    protected fun timeline(viewer: String, params: GetTimeline.Params = GetTimeline.Params(limit = 50)) = runBlocking {
        views.timeline(Did(viewer), params)
    }

    protected fun timelinePosts(viewer: String): List<PostView> = timeline(viewer).posts

    protected fun profilePosts(actor: String, viewer: String?): List<PostView> = runBlocking {
        views.authorPosts(viewer?.let(::Did), GetAuthorPosts.Params(actor)).posts
    }

    protected fun PostView.proposal() = embed as PostEmbedView.Proposal

    protected fun label(uri: String, value: String) = runBlocking {
        LabelSources.trusted = listOf(labeler)
        LabelStore(db, labeler, null).apply(labeler, 1, listOf(testLabel(labeler, uri, value)))
    }
}
