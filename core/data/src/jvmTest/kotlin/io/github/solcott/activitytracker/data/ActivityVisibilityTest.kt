package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.FakeAthletePds.Session
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityStreams
import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.data.db.FeedItemEntity
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ActivityViewerState
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.CommentRecord
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.RecordingView
import io.github.solcott.activitytracker.lexicon.app.ReplyRef
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Activity visibility in the data layer (`feed/activity-visibility`): where activities, their charts and the defaults are stored, and how a
 * visibility change moves them. The fake PDS holds Alice's repo and each of her tier spaces as `place → collection/rkey → record`.
 */
class ActivityVisibilityTest {
    private val pds = FakeAthletePds()
    private val did = pds.did
    private val repo = pds.repo
    private val stored = pds.stored
    private val writes = pds.writes
    private val announced = pds.announced
    private val created = pds.created
    private var failSpaceDeletes by pds::failSpaceDeletes
    private var offline by pds::offline
    private var served by pds::served
    private val streamsVisibility = pds.streamsVisibility

    private fun placesOf(collection: String, rkey: String) = pds.placesOf(collection, rkey)

    private suspend fun session(scope: String? = null) = pds.session(scope)

    private val run = ActivityType(Sport.RUN)

    private fun draft(choice: VisibilityChoice = VisibilityChoice.PUBLIC, withStreams: Boolean = true) =
        ActivityDraft(
                type = run,
                title = "Run",
                description = null,
                startedAt = Instant.parse("2026-10-14T07:00:00Z"),
                elapsed = 30.minutes,
                streams = ActivityStreamData(time = listOf(0, 1), heartRate = listOf(120, 121)).takeIf { withStreams },
            )
            .withVisibility(choice)

    private fun view(uri: AtUri, visibility: String, recordings: List<AtUri> = emptyList()) =
        ActivityView(
            uri = uri,
            cid = Cid("bafyrec"),
            author = ProfileViewBasic(did, Handle("alice.test")),
            record =
                ActivityRecord(
                    sport = Sport.RUN,
                    startedAt = Instant.parse("2026-10-14T07:00:00Z"),
                    elapsedTime = 1800,
                    createdAt = Clock.System.now(),
                ),
            likeCount = 2,
            commentCount = 1,
            indexedAt = Clock.System.now(),
            visibility = visibility,
            recordings = recordings.map { RecordingView(it, Instant.parse("2026-10-14T07:00:00Z")) }.ifEmpty { null },
        )

    @Test
    fun followersActivityIsPublishedToTheFollowersSpace() = runTest {
        val s = session()
        val uri = s.activities.publish(draft(VisibilityChoice(Visibility.FOLLOWERS, Visibility.SELF)))
        val rkey = uri.rkey!!
        assertEquals(setOf(Visibility.FOLLOWERS), placesOf(Nsid.FEED_ACTIVITY, rkey), "never in the repo")
        assertEquals(setOf(Visibility.SELF), placesOf(Nsid.FEED_ACTIVITY_STREAMS, rkey))
        assertEquals(listOf(Visibility.FOLLOWERS, Visibility.SELF), created)
        assertEquals(listOf(Visibility.FOLLOWERS, Visibility.SELF), announced, "the AppView learns of each tier space")
        assertEquals(uri, AtUri.of(did, Nsid.FEED_ACTIVITY, rkey), "one URI wherever it's stored")
    }

    @Test
    fun publicActivityIsPublishedToTheRepoOnly() = runTest {
        val s = session()
        val uri = s.activities.publish(draft(VisibilityChoice(Visibility.PUBLIC, Visibility.MUTUALS)))
        assertEquals(setOf(repo), placesOf(Nsid.FEED_ACTIVITY, uri.rkey!!))
        assertEquals(setOf(Visibility.MUTUALS), placesOf(Nsid.FEED_ACTIVITY_STREAMS, uri.rkey!!), "public run, private heart rate")
        val streams = stored.getValue(Visibility.MUTUALS).getValue("${Nsid.FEED_ACTIVITY_STREAMS}/${uri.rkey}")
        assertFalse("visibility" in streams, "where it's stored is its visibility")
    }

    @Test
    fun chartsAreNeverPublishedWiderThanTheActivity() = runTest {
        val s = session()
        val uri = s.activities.publish(draft().copy(visibility = Visibility.MUTUALS, streamsVisibility = Visibility.PUBLIC))
        assertEquals(setOf(Visibility.MUTUALS), placesOf(Nsid.FEED_ACTIVITY_STREAMS, uri.rkey!!))
    }

    @Test
    fun batchImportPublishesEveryActivityAtTheChosenVisibility() = runTest {
        val s = session()
        val choice = VisibilityChoice(Visibility.MUTUALS, Visibility.MUTUALS)
        val uris = s.activities.publishAll(List(3) { draft(choice, withStreams = false).copy(title = "Dive $it") })
        uris.forEach { assertEquals(setOf(Visibility.MUTUALS), placesOf(Nsid.FEED_ACTIVITY, it.rkey!!)) }
        assertEquals(listOf(Visibility.MUTUALS), created, "the space is created once")
    }

    @Test
    fun aSessionWithoutTheSpaceScopePublishesPublicOnly() = runTest {
        val s = session(scope = "atproto")
        assertEquals(false, s.clients.canUseSpaces)
        val e = assertFailsWith<XrpcException> { s.activities.publish(draft(VisibilityChoice(Visibility.FOLLOWERS, Visibility.FOLLOWERS))) }
        assertEquals(SpaceErrors.SCOPE_MISSING, e.error)
        assertEquals(emptyList(), writes, "nothing written, so nothing published wider than chosen")
        s.activities.publish(draft())
        assertTrue(writes.isNotEmpty())
        assertEquals(VisibilityChoice.PUBLIC, s.defaults.load(), "defaults can't be read: Public")
    }

    @Test
    fun narrowingTheActivityMovesItAndDragsItsCharts() = runTest {
        val s = session()
        val uri = s.activities.publish(draft(VisibilityChoice.PUBLIC))
        val rkey = uri.rkey!!
        streamsVisibility[rkey] = Visibility.PUBLIC
        writes.clear()

        s.manager.setVisibility(view(uri, Visibility.PUBLIC), VisibilityChoice(Visibility.FOLLOWERS, Visibility.PUBLIC))
        assertEquals(setOf(Visibility.FOLLOWERS), placesOf(Nsid.FEED_ACTIVITY, rkey))
        assertEquals(setOf(Visibility.FOLLOWERS), placesOf(Nsid.FEED_ACTIVITY_STREAMS, rkey), "charts narrowed to the activity")
        val streams = stored.getValue(Visibility.FOLLOWERS).getValue("${Nsid.FEED_ACTIVITY_STREAMS}/$rkey")
        assertFalse("visibility" in streams)
        assertEquals("bafyblob", streams.getValue("data").jsonObject.getValue("ref").jsonObject.getValue("\$link").jsonPrimitive.content)
        // Each record is written to its new place before it leaves the old one.
        assertEquals(
            listOf(
                "put followers ${Nsid.FEED_ACTIVITY}/$rkey",
                "delete repo ${Nsid.FEED_ACTIVITY}/$rkey",
                "put followers ${Nsid.FEED_ACTIVITY_STREAMS}/$rkey",
                "delete repo ${Nsid.FEED_ACTIVITY_STREAMS}/$rkey",
            ),
            writes,
        )
    }

    @Test
    fun anInterruptedMoveLeavesTheActivityStoredAndARetryFinishesIt() = runTest {
        val s = session()
        val uri = s.activities.publish(draft(VisibilityChoice(Visibility.MUTUALS, Visibility.MUTUALS), withStreams = false))
        val rkey = uri.rkey!!
        failSpaceDeletes = true
        assertFailsWith<XrpcException> {
            s.manager.setVisibility(view(uri, Visibility.MUTUALS), VisibilityChoice(Visibility.SELF, Visibility.SELF))
        }
        assertEquals(setOf(Visibility.MUTUALS, Visibility.SELF), placesOf(Nsid.FEED_ACTIVITY, rkey), "stored, in both for now")

        failSpaceDeletes = false
        s.manager.setVisibility(view(uri, Visibility.MUTUALS), VisibilityChoice(Visibility.SELF, Visibility.SELF))
        assertEquals(setOf(Visibility.SELF), placesOf(Nsid.FEED_ACTIVITY, rkey))
    }

    @Test
    fun aMoveFromAnOutdatedVisibilityFailsAndChangesNothing() = runTest {
        val s = session()
        // Moved to Mutuals elsewhere; this view still says Followers.
        val uri = s.activities.publish(draft(VisibilityChoice(Visibility.MUTUALS, Visibility.MUTUALS), withStreams = false))
        val rkey = uri.rkey!!
        writes.clear()

        assertFailsWith<XrpcException> {
            s.manager.setVisibility(view(uri, Visibility.FOLLOWERS), VisibilityChoice(Visibility.SELF, Visibility.SELF))
        }
        assertEquals(setOf(Visibility.MUTUALS), placesOf(Nsid.FEED_ACTIVITY, rkey))
        assertEquals(emptyList(), writes)
    }

    @Test
    fun recordingsMoveWithTheirPrimary() = runTest {
        val s = session()
        val primary = s.activities.publish(draft(withStreams = false))
        val recording =
            s.activities.publish(
                draft(withStreams = false)
                    .recordingOf(
                        OwnActivity(
                            primary,
                            null,
                            ActivityRecord(Sport.RUN, startedAt = Clock.System.now(), elapsedTime = 1, createdAt = Clock.System.now()),
                        )
                    )
            )
        s.manager.setVisibility(
            view(primary, Visibility.PUBLIC, recordings = listOf(recording)),
            VisibilityChoice(Visibility.SELF, Visibility.SELF),
        )
        assertEquals(setOf(Visibility.SELF), placesOf(Nsid.FEED_ACTIVITY, primary.rkey!!))
        assertEquals(setOf(Visibility.SELF), placesOf(Nsid.FEED_ACTIVITY, recording.rkey!!))
    }

    @Test
    fun groupingIntoAFollowersActivityMakesTheRecordingFollowers() = runTest {
        val s = session()
        val primary =
            OwnActivity(
                AtUri.of(did, Nsid.FEED_ACTIVITY, "3kprimary"),
                Cid("bafyrec"),
                view(AtUri("at://x/y/z"), Visibility.FOLLOWERS).record,
                Visibility.FOLLOWERS,
            )
        val grouped = draft(VisibilityChoice.PUBLIC).recordingOf(primary)
        assertEquals(Visibility.FOLLOWERS, grouped.visibility)
        assertEquals(Visibility.FOLLOWERS, grouped.publishedStreamsVisibility, "charts capped too")
        val uri = s.activities.publish(grouped)
        assertEquals(setOf(Visibility.FOLLOWERS), placesOf(Nsid.FEED_ACTIVITY, uri.rkey!!))
    }

    @Test
    fun streamsMoveBetweenTheRepoAndASpaceAndBack() = runTest {
        val s = session()
        val uri = AtUri.of(did, Nsid.FEED_ACTIVITY, "3kstreams")
        s.streams.put(uri, ActivityStreams(ActivityStreamData(time = listOf(0)), Visibility.PUBLIC))
        s.activities.sync()
        assertEquals(setOf(repo), placesOf(Nsid.FEED_ACTIVITY_STREAMS, "3kstreams"))
        s.streams.move(uri, Visibility.PUBLIC, Visibility.FOLLOWERS)
        assertEquals(setOf(Visibility.FOLLOWERS), placesOf(Nsid.FEED_ACTIVITY_STREAMS, "3kstreams"))
        s.streams.move(uri, Visibility.FOLLOWERS, Visibility.PUBLIC)
        assertEquals(setOf(repo), placesOf(Nsid.FEED_ACTIVITY_STREAMS, "3kstreams"))
        val record = stored.getValue(repo).getValue("${Nsid.FEED_ACTIVITY_STREAMS}/3kstreams")
        assertFalse("visibility" in record)
    }

    @Test
    fun deletingASpaceActivityClearsItAndItsStreamsWhereverTheyAre() = runTest {
        val s = session()
        val uri = s.activities.publish(draft(VisibilityChoice(Visibility.MUTUALS, Visibility.SELF)))
        s.manager.delete(view(uri, Visibility.MUTUALS), withRecordings = false)
        assertEquals(emptySet(), placesOf(Nsid.FEED_ACTIVITY, uri.rkey!!))
        assertEquals(emptySet(), placesOf(Nsid.FEED_ACTIVITY_STREAMS, uri.rkey!!))
        assertEquals(0, s.activities.sync(), "deletes from spaces that never existed count as done")
    }

    @Test
    fun defaultsAreReadOnceSavedCappedAndPublicWhenUnreadable() = runTest {
        val s = session()
        assertEquals(VisibilityChoice.PUBLIC, s.defaults.load(), "never set")
        assertEquals(
            VisibilityChoice(Visibility.MUTUALS, Visibility.MUTUALS),
            s.defaults.save(VisibilityChoice(Visibility.MUTUALS, Visibility.PUBLIC)),
        )
        val record = stored.getValue(Visibility.SELF).getValue("${Nsid.ACTOR_ACTIVITY_DEFAULTS}/self")
        assertEquals(Visibility.MUTUALS, record.getValue("chartsVisibility").jsonPrimitive.content, "charts default capped")

        // Another device signs in: it reads the same defaults.
        assertEquals(VisibilityChoice(Visibility.MUTUALS, Visibility.MUTUALS), session().defaults.load())

        offline = true
        assertEquals(VisibilityChoice.PUBLIC, session().defaults.load(), "unreadable: Public, without blocking")
    }

    private val bob = Did("did:plc:bob")
    private val carol = Did("did:plc:carol")

    /** One of Bob's activities, as Alice sees it at [visibility], cached in her feed. */
    private suspend fun Session.bobs(visibility: String, rkey: String = "3kbob"): ActivityView {
        val view =
            view(AtUri.of(bob, Nsid.FEED_ACTIVITY, rkey), visibility)
                .copy(author = ProfileViewBasic(bob, Handle("bob.test")), likeCount = 0, commentCount = 0, viewer = ActivityViewerState())
        db.feed().upsert(listOf(FeedItemEntity("timeline", view.uri.value, 0, AtJson.encodeToString(ActivityView.serializer(), view))))
        return view
    }

    @Test
    fun aLikeOnANonPublicActivityGoesInTheInteractionsSpace() = runTest {
        val s = session()
        val followers = s.bobs(Visibility.FOLLOWERS)
        s.activities.toggleLike(followers)

        val likes = writes.filter { Nsid.FEED_LIKE in it }
        assertEquals(1, likes.size)
        assertTrue(likes.single().startsWith("put ${AthleteSpace.INTERACTIONS} ${Nsid.FEED_LIKE}/"), likes.single())
        assertEquals(listOf(AthleteSpace.INTERACTIONS), created, "the space is created on the first such like")
        assertEquals(listOf(AthleteSpace.INTERACTIONS), announced, "and the AppView told it exists")
        val cached = s.cached(followers)
        assertEquals(1, cached.likeCount)
        assertTrue(cached.viewer?.like != null, "liked, for Alice")
    }

    @Test
    fun aLikeOnAPublicActivityGoesInTheRepo() = runTest {
        val s = session()
        s.activities.toggleLike(s.bobs(Visibility.PUBLIC))
        assertEquals(listOf("put repo ${Nsid.FEED_LIKE}"), writes.map { it.substringBeforeLast('/') })
        assertEquals(emptyList(), created)
    }

    @Test
    fun aStaleViewPlacesTheLikeByWhatItShows() = runTest {
        val s = session()
        // Bob just made it Followers; Alice's view still says Public, so the like goes in her repo, and the AppView shows it by the tier.
        s.activities.toggleLike(s.bobs(Visibility.PUBLIC))
        assertEquals(listOf("put repo ${Nsid.FEED_LIKE}"), writes.map { it.substringBeforeLast('/') })
    }

    @Test
    fun unlikingDeletesFromBothPlaces() = runTest {
        val s = session()
        val followers = s.bobs(Visibility.FOLLOWERS)
        s.activities.toggleLike(followers)
        val rkey = writes.single().substringAfterLast('/')
        writes.clear()

        // The activity was made public since: the like is still in the space, and unliking finds it there.
        s.activities.toggleLike(followers.copy(visibility = Visibility.PUBLIC))
        assertEquals(
            listOf("delete repo ${Nsid.FEED_LIKE}/$rkey", "delete ${AthleteSpace.INTERACTIONS} ${Nsid.FEED_LIKE}/$rkey"),
            writes,
        )
        assertEquals(emptySet(), placesOf(Nsid.FEED_LIKE, rkey))
        val cached = s.cached(followers)
        assertEquals(0, cached.likeCount)
        assertEquals(null, cached.viewer?.like)
    }

    @Test
    fun unlikingWithNoInteractionsSpaceCountsAsDone() = runTest {
        val s = session()
        s.activities.toggleLike(s.bobs(Visibility.PUBLIC))
        val rkey = writes.single().substringAfterLast('/')
        s.activities.toggleLike(s.bobs(Visibility.PUBLIC))
        assertEquals(listOf("put repo ${Nsid.FEED_LIKE}/$rkey", "delete repo ${Nsid.FEED_LIKE}/$rkey"), writes)
        assertEquals(0, s.db.outbox().pending(did.value).size, "SpaceNotFound for the space delete doesn't hold up the outbox")
    }

    @Test
    fun aSessionWithoutTheSpaceScopeLikesPublicActivitiesOnly() = runTest {
        val s = session(scope = "atproto")
        val followers = s.bobs(Visibility.FOLLOWERS)
        val e = assertFailsWith<XrpcException> { s.activities.toggleLike(followers) }
        assertEquals(SpaceErrors.SCOPE_MISSING, e.error)
        assertEquals(emptyList(), writes, "nothing written to the repo")
        assertEquals(0, s.cached(followers).likeCount, "nor shown")

        val public = s.bobs(Visibility.PUBLIC, rkey = "3kpub")
        s.activities.toggleLike(public)
        s.activities.toggleLike(public)
        assertEquals(listOf("put repo ${Nsid.FEED_LIKE}", "delete repo ${Nsid.FEED_LIKE}"), writes.map { it.substringBeforeLast('/') })
        assertEquals(0, s.db.outbox().pending(did.value).size)
    }

    @Test
    fun aSessionWhoseSpaceScopeLacksLikesLikesPublicActivitiesOnly() = runTest {
        // Signed in before the declaration listed feed.like: the PDS filled in only the collections declared then, and would refuse it.
        val s =
            session(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}&collection=${Nsid.ACTOR_PRIVATE_DETAILS}")
        assertEquals(true, s.clients.canUseSpaces)
        val followers = s.bobs(Visibility.FOLLOWERS)
        val e = assertFailsWith<XrpcException> { s.activities.toggleLike(followers) }
        assertEquals(SpaceErrors.SCOPE_MISSING, e.error)
        assertEquals(emptyList(), writes, "nothing written")
        assertEquals(emptyList(), created, "nor a space created")
        assertEquals(0, s.cached(followers).likeCount, "nor shown")
        assertEquals(0, s.db.outbox().pending(did.value).size, "nor left in the outbox to hold up later writes")

        val public = s.bobs(Visibility.PUBLIC, rkey = "3kpub")
        s.activities.toggleLike(public)
        s.activities.toggleLike(public)
        assertEquals(listOf("put repo ${Nsid.FEED_LIKE}", "delete repo ${Nsid.FEED_LIKE}"), writes.map { it.substringBeforeLast('/') })
        assertEquals(0, s.db.outbox().pending(did.value).size)
    }

    @Test
    fun aSpaceScopeListingLikesOrAnyCollectionCoversLikes() = runTest {
        for (collections in listOf("&collection=${Nsid.FEED_LIKE}&collection=${Nsid.FEED_ACTIVITY}", "&collection=*", "")) {
            assertTrue(session(scope = OAuthClientConfig.DEFAULT_SCOPE + collections).clients.canWriteToSpaces(Nsid.FEED_LIKE), collections)
        }
        assertEquals(false, session(scope = "atproto").clients.canWriteToSpaces(Nsid.FEED_LIKE))
    }

    /** Alice's one comment stored in [place]. */
    private fun storedComment(place: String): JsonObject =
        stored.getValue(place).filterKeys { it.startsWith("${Nsid.FEED_COMMENT}/") }.values.single()

    @Test
    fun aCommentOnANonPublicActivityGoesInTheInteractionsSpaceWithItsAudience() = runTest {
        val s = session()
        val followers = s.bobs(Visibility.FOLLOWERS)
        s.activities.comment(followers, "  Nice  ")

        val comments = writes.filter { Nsid.FEED_COMMENT in it }
        assertEquals(1, comments.size)
        assertTrue(comments.single().startsWith("put ${AthleteSpace.INTERACTIONS} ${Nsid.FEED_COMMENT}/"), comments.single())
        assertEquals(listOf(AthleteSpace.INTERACTIONS), created, "the space is created on the first such comment")
        assertEquals(listOf(AthleteSpace.INTERACTIONS), announced)
        val record = storedComment(AthleteSpace.INTERACTIONS)
        assertEquals(Visibility.FOLLOWERS, record.getValue("audience").jsonPrimitive.content)
        assertEquals("Nice", record.getValue("text").jsonPrimitive.content)
        assertEquals(1, s.cached(followers).commentCount)
    }

    @Test
    fun aCommentOnAPublicActivityGoesInTheRepoWithNoAudience() = runTest {
        val s = session()
        s.activities.comment(s.bobs(Visibility.PUBLIC), "Nice")
        assertEquals(listOf("put repo ${Nsid.FEED_COMMENT}"), writes.map { it.substringBeforeLast('/') })
        assertEquals(null, storedComment(repo)["audience"])
        assertEquals(emptyList(), created)
    }

    @Test
    fun aStaleViewPlacesTheCommentAndItsAudienceByWhatItShows() = runTest {
        val s = session()
        // Bob just made it Public; Alice's view still says Mutuals, so the comment is written for Mutuals, and stays that narrow.
        s.activities.comment(s.bobs(Visibility.MUTUALS), "Nice")
        assertEquals(listOf("put ${AthleteSpace.INTERACTIONS} ${Nsid.FEED_COMMENT}"), writes.map { it.substringBeforeLast('/') })
        assertEquals(Visibility.MUTUALS, storedComment(AthleteSpace.INTERACTIONS).getValue("audience").jsonPrimitive.content)
    }

    @Test
    fun aSessionWhoseSpaceScopeLacksCommentsCommentsOnPublicActivitiesOnly() = runTest {
        // Signed in before the declaration listed feed.comment.
        val s = session(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}&collection=${Nsid.FEED_LIKE}")
        val followers = s.bobs(Visibility.FOLLOWERS)
        val e = assertFailsWith<XrpcException> { s.activities.comment(followers, "Nice") }
        assertEquals(SpaceErrors.SCOPE_MISSING, e.error)
        assertEquals(emptyList(), writes, "nothing written")
        assertEquals(emptyList(), created, "nor a space created")
        assertEquals(0, s.cached(followers).commentCount, "nor counted")
        assertEquals(0, s.db.outbox().pending(did.value).size, "nor left in the outbox")

        s.activities.comment(s.bobs(Visibility.PUBLIC, rkey = "3kpub"), "Nice")
        assertEquals(listOf("put repo ${Nsid.FEED_COMMENT}"), writes.map { it.substringBeforeLast('/') })
    }

    /** Carol's comment on bob's activity, written for [audience]; with [root], a reply in [root]'s thread. */
    private fun carols(rkey: String, audience: String? = null, root: CommentView? = null) =
        commentOn(carol, rkey).copy(audience = audience, root = root?.ref, parent = root?.ref)

    @Test
    fun aReplyToAFollowersCommentOnAPublicActivityGoesInTheInteractionsSpace() = runTest {
        val s = session()
        val parent = carols("3kc1", audience = Visibility.FOLLOWERS)
        s.activities.comment(s.bobs(Visibility.PUBLIC), "Agreed", replyTo = parent)
        assertEquals(listOf("put ${AthleteSpace.INTERACTIONS} ${Nsid.FEED_COMMENT}"), writes.map { it.substringBeforeLast('/') })
        val record = AtJson.decodeFromJsonElement(CommentRecord.serializer(), storedComment(AthleteSpace.INTERACTIONS))
        assertEquals(Visibility.FOLLOWERS, record.audience, "capped by its parent's audience")
        assertEquals(ReplyRef(parent.ref, parent.ref), record.reply)
    }

    @Test
    fun aReplyToAPublicCommentOnAPublicActivityGoesInTheRepo() = runTest {
        val s = session()
        val parent = carols("3kc1")
        s.activities.comment(s.bobs(Visibility.PUBLIC), "Agreed", replyTo = parent)
        assertEquals(listOf("put repo ${Nsid.FEED_COMMENT}"), writes.map { it.substringBeforeLast('/') })
        val record = AtJson.decodeFromJsonElement(CommentRecord.serializer(), storedComment(repo))
        assertNull(record.audience)
        assertEquals(ReplyRef(parent.ref, parent.ref), record.reply)
    }

    @Test
    fun aReplyToAPublicCommentOnAMutualsActivityIsForMutuals() = runTest {
        val s = session()
        s.activities.comment(s.bobs(Visibility.MUTUALS), "Agreed", replyTo = carols("3kc1"))
        val record = AtJson.decodeFromJsonElement(CommentRecord.serializer(), storedComment(AthleteSpace.INTERACTIONS))
        assertEquals(Visibility.MUTUALS, record.audience)
    }

    @Test
    fun aNonPublicReplyIsRefusedWithoutTheScopeAndWritesNothing() = runTest {
        val s = session(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}&collection=${Nsid.FEED_LIKE}")
        val public = s.bobs(Visibility.PUBLIC)
        val e =
            assertFailsWith<XrpcException> {
                s.activities.comment(public, "Agreed", replyTo = carols("3kc1", audience = Visibility.FOLLOWERS))
            }
        assertEquals(SpaceErrors.SCOPE_MISSING, e.error)
        assertEquals(emptyList(), writes, "nothing written")
        assertEquals(0, s.cached(public).commentCount, "nor counted")
        assertEquals(emptyList(), s.feed.localComments.sent(public.uri), "nor listed")
        assertEquals(0, s.db.outbox().pending(did.value).size, "nor left in the outbox")
    }

    @Test
    fun aReplyToAReplyKeepsItsThreadsRoot() = runTest {
        val s = session()
        val root = commentOn(bob, "3kc1")
        val reply = carols("3kc2", root = root)
        val activity = s.bobs(Visibility.PUBLIC)
        val uri = s.activities.comment(activity, "Me too", replyTo = reply)
        val record = AtJson.decodeFromJsonElement(CommentRecord.serializer(), storedComment(repo))
        assertEquals(ReplyRef(root = root.ref, parent = reply.ref), record.reply)
        assertEquals(root.uri, s.feed.localComments.sent(activity.uri).single { it.uri == uri }.root)
    }

    @Test
    fun changingVisibilityKeepsTheLikesAndComments() = runTest {
        val s = session()
        val uri = s.activities.publish(draft(VisibilityChoice.PUBLIC, withStreams = false))
        val own = view(uri, Visibility.PUBLIC).copy(viewer = ActivityViewerState())
        s.db.feed().upsert(listOf(FeedItemEntity("timeline", uri.value, 0, AtJson.encodeToString(ActivityView.serializer(), own))))

        s.manager.setVisibility(own, VisibilityChoice(Visibility.FOLLOWERS, Visibility.FOLLOWERS))
        val cached = s.cached(own)
        assertEquals(Visibility.FOLLOWERS, cached.visibility)
        assertEquals(2, cached.likeCount)
        assertEquals(1, cached.commentCount, "the AppView recounts both for the new audience")
    }

    /** A comment by [author] with rkey [rkey], as the AppView lists it. */
    private fun commentOn(author: Did, rkey: String) =
        CommentView(
            AtUri.of(author, Nsid.FEED_COMMENT, rkey),
            Cid("bafy$rkey"),
            ProfileViewBasic(author, Handle("${author.value.substringAfterLast(':')}.test")),
            "Nice",
            Instant.parse("2026-10-14T08:00:00Z"),
        )

    @Test
    fun aDeletedCommentStaysHiddenUntilTheAppViewDropsIt() = runTest {
        val s = session()
        val activity = s.bobs(Visibility.FOLLOWERS)
        val mine = commentOn(did, "3kc1")
        val bobs = commentOn(bob, "3kc2")
        served = GetActivity.Output(activity.copy(commentCount = 2), listOf(mine, bobs))
        s.activities.deleteComment(activity, mine.uri)

        assertEquals(1, s.feed.activity(activity.uri).commentCount)
        assertEquals(listOf(bobs), s.listed(activity), "the AppView still lists it, but it's left out")
        assertEquals(1, s.cached(activity).commentCount)

        served = GetActivity.Output(activity.copy(commentCount = 1), listOf(bobs))
        assertEquals(listOf(bobs), s.listed(activity))
        assertEquals(1, s.feed.activity(activity.uri).commentCount, "not taken off twice once the AppView has dropped it")
    }

    /** The first page of [activity]'s comments as the AppView serves them, laid over with Alice's own changes. */
    private suspend fun Session.listed(activity: ActivityView): List<CommentView> =
        feed.localComments.applyTo(feed.comments(activity.uri).comments)

    /** Alice's last comment, as the AppView would list it once indexed, from the rkey she wrote. */
    private fun ownComment(): CommentView {
        val rkey = writes.last { Nsid.FEED_COMMENT in it }.substringAfterLast('/')
        return commentOn(did, rkey)
    }

    @Test
    fun deletingACommentDeletesFromBothPlaces() = runTest {
        val s = session()
        val followers = s.bobs(Visibility.FOLLOWERS)
        s.activities.comment(followers, "Nice")
        val comment = ownComment()
        writes.clear()

        // The activity was made public since: the comment is still in the space, and deleting finds it there.
        s.activities.deleteComment(followers.copy(visibility = Visibility.PUBLIC), comment.uri)
        val rkey = comment.uri.rkey
        assertEquals(
            listOf("delete repo ${Nsid.FEED_COMMENT}/$rkey", "delete ${AthleteSpace.INTERACTIONS} ${Nsid.FEED_COMMENT}/$rkey"),
            writes,
        )
        assertEquals(emptySet(), placesOf(Nsid.FEED_COMMENT, rkey!!))
        assertEquals(0, s.cached(followers).commentCount)
        assertEquals(0, s.db.outbox().pending(did.value).size)
    }

    @Test
    fun deletingACommentWithNoInteractionsSpaceCountsAsDone() = runTest {
        val s = session()
        val public = s.bobs(Visibility.PUBLIC)
        s.activities.comment(public, "Nice")
        val comment = ownComment()
        s.activities.deleteComment(public, comment.uri)
        assertEquals(
            listOf("put repo ${Nsid.FEED_COMMENT}/${comment.uri.rkey}", "delete repo ${Nsid.FEED_COMMENT}/${comment.uri.rkey}"),
            writes,
        )
        assertEquals(0, s.db.outbox().pending(did.value).size, "SpaceNotFound for the space delete doesn't hold up the outbox")
    }

    @Test
    fun aDeleteWaitsInTheOutboxWhileOffline() = runTest {
        val s = session()
        val public = s.bobs(Visibility.PUBLIC)
        s.activities.comment(public, "Nice")
        val comment = ownComment()
        offline = true
        s.activities.deleteComment(public, comment.uri)
        assertEquals(2, s.db.outbox().pending(did.value).size, "both deletes kept")
        served = GetActivity.Output(public.copy(commentCount = 1), listOf(comment))
        offline = false
        assertEquals(emptyList(), s.listed(public), "left out meanwhile")
        s.activities.sync()
        assertEquals(emptySet(), placesOf(Nsid.FEED_COMMENT, comment.uri.rkey!!))
        assertEquals(0, s.db.outbox().pending(did.value).size)
    }

    @Test
    fun aSessionWhoseSpaceScopeLacksCommentsDeletesFromTheRepoOnly() = runTest {
        val s = session(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}&collection=${Nsid.FEED_LIKE}")
        val followers = s.bobs(Visibility.FOLLOWERS)
        // Written by another, newer session of Alice's.
        val comment = commentOn(did, "3kc9")
        s.activities.deleteComment(followers, comment.uri)
        assertEquals(listOf("delete repo ${Nsid.FEED_COMMENT}/3kc9"), writes)
        assertEquals(0, s.db.outbox().pending(did.value).size, "nothing left waiting on a space write the PDS would refuse")
    }

    @Test
    fun someoneElsesCommentIsNotDeleted() = runTest {
        val s = session()
        val followers = s.bobs(Visibility.FOLLOWERS).copy(commentCount = 1)
        s.activities.deleteComment(followers, commentOn(bob, "3kc2").uri)
        assertEquals(emptyList(), writes)
        assertEquals(0, s.db.outbox().pending(did.value).size)
    }
}
