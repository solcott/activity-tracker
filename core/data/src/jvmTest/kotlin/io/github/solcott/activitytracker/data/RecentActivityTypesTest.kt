package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.db.FeedItemEntity
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class RecentActivityTypesTest {
    private val did = Did("did:plc:alice")
    private val http = HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) })

    private fun ownActivity(rkey: String, sport: String, subSport: String? = null) =
        ActivityView(
            uri = AtUri.of(did, Nsid.FEED_ACTIVITY, rkey),
            cid = Cid("bafy$rkey"),
            author = ProfileViewBasic(did, Handle("alice.test")),
            record =
                ActivityRecord(
                    sport = sport,
                    subSport = subSport,
                    startedAt = Instant.parse("2026-09-30T07:00:00Z"),
                    elapsedTime = 1800,
                    createdAt = Instant.parse("2026-09-30T07:31:00Z"),
                ),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse("2026-09-30T07:32:00Z"),
        )

    @Test
    fun startsFromOwnCachedActivitiesThenFollowsWhatIsPublished() = runTest {
        val (db, _, clients) = signedInTestSession(http, did = did)
        val cached =
            listOf(
                ownActivity("3k1", Sport.RIDE, SubSport.MOUNTAIN),
                ownActivity("3k2", Sport.RUN),
                ownActivity("3k3", Sport.RIDE, SubSport.MOUNTAIN),
            )
        db.feed()
            .upsert(
                cached.mapIndexed { i, view ->
                    FeedItemEntity("author:${did.value}", view.uri.value, i, AtJson.encodeToString(ActivityView.serializer(), view))
                }
            )
        val recent = RecentActivityTypes(db, clients, FeedRepository(db, clients, LocalLikes(), Logger))

        assertEquals(listOf(ActivityType(Sport.RIDE, SubSport.MOUNTAIN), ActivityType(Sport.RUN)), recent.types.first())

        recent.remember(ActivityType(Sport.SWIM))
        recent.remember(ActivityType(Sport.YOGA))
        recent.remember(ActivityType(Sport.SWIM))
        assertEquals(listOf(ActivityType(Sport.SWIM), ActivityType(Sport.YOGA)), recent.types.first(), "newest first, no repeats")
    }
}
