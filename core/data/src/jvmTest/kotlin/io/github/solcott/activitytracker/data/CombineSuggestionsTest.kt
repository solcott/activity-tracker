package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.ActivityTrackStore
import io.github.solcott.activitytracker.data.activity.CombineCue
import io.github.solcott.activitytracker.data.activity.CombineSubject
import io.github.solcott.activitytracker.data.activity.CombineSuggestions
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CombinedActivity
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivitiesByStart
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.TrackMath
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class CombineSuggestionsTest {
    private val did = Did("did:plc:alice")
    private val now = Instant.parse("2026-10-19T18:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = now
        }
    private val today = Instant.parse("2026-10-19T09:00:00Z")

    /** What the AppView serves for the athlete's activities by start. */
    private var served = listOf<ActivityView>()

    /** The athlete's own tracks, by activity. */
    private val ownTracks = mutableMapOf<AtUri, List<LocationSample>>()

    private fun uri(rkey: String) = AtUri.of(did, Nsid.FEED_ACTIVITY, rkey)

    private fun record(
        start: Instant,
        length: Duration,
        sport: String = Sport.SKI,
        polyline: String? = null,
        combined: List<String>? = null,
    ) =
        ActivityRecord(
            sport = sport,
            startedAt = start,
            elapsedTime = length.inWholeSeconds,
            createdAt = start,
            polyline = polyline,
            combined = combined?.let { members -> CombinedActivity(members.map(::uri)) },
        )

    private fun view(rkey: String, record: ActivityRecord, combinedInto: String? = null) =
        ActivityView(
            uri = uri(rkey),
            cid = Cid("bafy$rkey"),
            author = ProfileViewBasic(did, Handle("alice.test")),
            record = record,
            likeCount = 0,
            commentCount = 0,
            indexedAt = record.startedAt,
            combinedInto = combinedInto?.let(::uri),
        )

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/${Nsid.FEED_GET_AUTHOR_ACTIVITIES_BY_START}" -> {
                        val since = Instant.parse(request.url.parameters["since"]!!)
                        val until = Instant.parse(request.url.parameters["until"]!!)
                        val output = GetAuthorActivitiesByStart.Output(served.filter { it.record.startedAt in since..<until })
                        respond(AtJson.encodeToString(GetAuthorActivitiesByStart.Output.serializer(), output), headers = json)
                    }
                    else -> respondError(HttpStatusCode.NotFound)
                }
            }
        )

    private val tracks =
        object : ActivityTrackStore {
            override suspend fun put(activity: AtUri, gpx: ByteArray) = Unit

            override suspend fun has(activity: AtUri) = activity in ownTracks

            override suspend fun delete(activity: AtUri) = Unit

            override suspend fun read(activity: AtUri) = ownTracks[activity]
        }

    private suspend fun suggestions(): CombineSuggestions {
        val (db, _, clients) = signedInTestSession(http, did = did)
        return CombineSuggestions(db, clients, FeedRepository(db, clients, LocalLikes(), Logger), tracks, clock)
    }

    @Test
    fun aSkiDayAfterLunchIsSuggestedAsABreak() = runTest {
        val morning = view("3km", record(today, 2.hours + 45.minutes))
        served = listOf(morning)
        val afternoon = CombineSubject(uri("3ka"), record(today + 4.hours, 3.hours))

        val suggestion = suggestions().forActivity(afternoon)!!

        assertEquals(morning.uri, suggestion.shown.uri)
        assertEquals(CombineCue.Break, suggestion.cue)
    }

    @Test
    fun aRideFromLastMonthGetsNothing() = runTest {
        val start = now - 30.days
        served = listOf(view("3kr1", record(start, 1.hours, Sport.RIDE)))

        assertNull(suggestions().forActivity(CombineSubject(uri("3kr2"), record(start + 90.minutes, 1.hours, Sport.RIDE))))
    }

    @Test
    fun membersOfOneCombinedDayArentPaired() = runTest {
        val day = view("3kday", record(today, 7.hours, combined = listOf("3km", "3ka")))
        val morning = view("3km", record(today, 2.hours + 45.minutes), combinedInto = "3kday")
        val afternoon = view("3ka", record(today + 4.hours, 3.hours), combinedInto = "3kday")
        served = listOf(day, morning, afternoon)

        assertNull(suggestions().forActivity(afternoon))
        assertNull(suggestions().forActivity(day))
    }

    @Test
    fun aMemberIsOfferedAsItsCombinedDay() = runTest {
        val day = view("3kday", record(today, 7.hours, combined = listOf("3km", "3ka")))
        val morning = view("3km", record(today, 2.hours + 45.minutes), combinedInto = "3kday")
        val afternoon = view("3ka", record(today + 4.hours, 3.hours), combinedInto = "3kday")
        served = listOf(day, morning, afternoon)
        val evening = CombineSubject(uri("3ke"), record(today + 7.hours + 30.minutes, 1.hours))

        val suggestion = suggestions().forActivity(evening)!!

        assertEquals(day.uri, suggestion.shown.uri)
    }

    @Test
    fun aDismissedPairGetsNothingFromEitherSide() = runTest {
        val first = view("3kr1", record(today, 1.hours, Sport.RIDE))
        val second = view("3kr2", record(today + 90.minutes, 1.hours, Sport.RIDE))
        served = listOf(first, second)
        val suggestions = suggestions()
        val suggestion = suggestions.forActivity(second)!!

        suggestions.dismiss(CombineSubject(second), suggestion)

        assertNull(suggestions.forActivity(second))
        assertNull(suggestions.forActivity(first))
    }

    @Test
    fun aTrimmedPolylineWithoutAnOwnTrackIsABreak() = runTest {
        fun at(lat: Double, lon: Double) = LocationSample(lat, lon, 400.0, null, null, today)
        val stop = at(46.0, 7.0)
        // Trimmed by a privacy zone at the stop: the polyline ends a kilometer short of it.
        val watch =
            view("3kw", record(today, 1.hours, Sport.RIDE, polyline = TrackMath.encodePolyline(listOf(at(45.98, 7.0), at(45.991, 7.0)))))
        served = listOf(watch)
        val phone =
            CombineSubject(
                uri("3kp"),
                record(today + 1.hours + 2.minutes, 1.hours, Sport.RIDE, polyline = TrackMath.encodePolyline(listOf(stop, at(46.02, 7.0)))),
            )

        assertEquals(CombineCue.Break, suggestions().forActivity(phone)!!.cue)

        ownTracks[watch.uri] = listOf(at(45.98, 7.0), stop)
        assertEquals(CombineCue.SwitchedDevices, suggestions().forActivity(phone)!!.cue)
    }
}
