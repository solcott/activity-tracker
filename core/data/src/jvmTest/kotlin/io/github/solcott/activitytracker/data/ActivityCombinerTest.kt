package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.activity.ActivityCombiner
import io.github.solcott.activitytracker.data.activity.CombineMember
import io.github.solcott.activitytracker.data.activity.CombinedRoute
import io.github.solcott.activitytracker.data.activity.PrivacySettings
import io.github.solcott.activitytracker.data.activity.RouteTrim
import io.github.solcott.activitytracker.data.activity.SegmentedPolyline
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ChannelSource
import io.github.solcott.activitytracker.lexicon.app.CombinedChannel
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.GasSwitch
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.Gpx
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** `feed/combined-activities`: how a combined activity is built from its members. */
class ActivityCombinerTest {
    private val now = Instant.parse("2026-10-14T20:00:00Z")

    private fun uri(rkey: String) = AtUri("at://did:plc:alice/${Nsid.FEED_ACTIVITY}/$rkey")

    private fun at(time: String) = Instant.parse("2026-10-14T${time}:00Z")

    /** A member from [from] to [to] (HH:MM), with a sample every [every] seconds of each channel [channels] gives as a function of t. */
    @Suppress("LongParameterList")
    private fun member(
        rkey: String,
        from: String,
        to: String,
        every: Int = 1,
        record: (ActivityRecord) -> ActivityRecord = { it },
        streams: ((ActivityStreamData) -> ActivityStreamData)? = null,
        track: Boolean = false,
        sport: String = Sport.RIDE,
    ): CombineMember {
        val start = at(from)
        val length = (at(to) - start).inWholeSeconds
        val base = ActivityRecord(sport = sport, startedAt = start, elapsedTime = length, createdAt = start, title = rkey)
        val time = (0..length.toInt() step every).toList()
        return CombineMember(
            uri = uri(rkey),
            record = record(base),
            streams = streams?.invoke(ActivityStreamData(time = time)),
            track = if (track) time.map { t -> point(start + t.seconds, t) } else null,
        )
    }

    private fun point(time: Instant, t: Int) = LocationSample(46.0 + t * 1e-5, 7.0, 1500.0, null, null, time)

    @Test
    fun overlappingMembersAreOneGroupAndBackToBackOnesAreSeveral() {
        val watch = member("watch", "09:00", "11:00")
        val edge = member("edge", "08:58", "11:01")
        assertEquals(1, ActivityCombiner.groups(listOf(watch, edge)).size)

        val runs = listOf(member("run1", "10:00", "10:20"), member("run2", "10:40", "11:00"), member("run3", "11:20", "11:40"))
        assertEquals(3, ActivityCombiner.groups(runs).size)
    }

    @Test
    fun aMemberOverlappingByLessThanHalfStartsANewGroup() {
        val watch = member("watch", "09:00", "10:40")
        val phone = member("phone", "10:38", "12:00")
        assertEquals(
            listOf(listOf("watch"), listOf("phone")),
            ActivityCombiner.groups(listOf(phone, watch)).map { g -> g.map { it.record.title } },
        )
    }

    @Test
    fun theDefaultRankingPrefersMoreSamplesThenTheEarlierStart() {
        val watch = member("watch", "09:00", "10:00", every = 5, streams = { d -> d.copy(heartRate = d.time.map { 120 }) })
        val edge = member("edge", "09:01", "10:00", streams = { d -> d.copy(heartRate = d.time.map { 130 }, power = d.time.map { 200 }) })
        val rankings = ActivityCombiner.rankings(listOf(watch, edge))
        assertEquals(listOf(uri("edge"), uri("watch")), rankings[StreamChannel.HEART_RATE])
        assertEquals(listOf(uri("edge")), rankings[StreamChannel.POWER])

        val same = member("same", "09:01", "10:00", every = 5, streams = { d -> d.copy(heartRate = d.time.map { 125 }) })
        assertEquals(listOf(uri("watch"), uri("same")), ActivityCombiner.rankings(listOf(same, watch))[StreamChannel.HEART_RATE])
    }

    @Test
    fun aStoredRankingIsKeptAndMembersItDoesntNameFollow() {
        val watch = member("watch", "09:00", "10:00", streams = { d -> d.copy(heartRate = d.time.map { 120 }) })
        val edge = member("edge", "09:00", "10:00", every = 2, streams = { d -> d.copy(heartRate = d.time.map { 130 }) })
        val rankings =
            ActivityCombiner.rankings(
                listOf(watch, edge),
                listOf(ChannelSource(StreamChannel.HEART_RATE, listOf(uri("edge"), uri("gone")))),
            )
        assertEquals(listOf(uri("edge"), uri("watch")), rankings[StreamChannel.HEART_RATE])
    }

    @Test
    fun strapHeartRateAndMeterPower() {
        val watch = member("watch", "09:00", "10:00", every = 2, streams = { d -> d.copy(heartRate = d.time.map { 120 }) })
        val edge = member("edge", "09:00", "10:00", streams = { d -> d.copy(heartRate = d.time.map { 140 }, power = d.time.map { 210 }) })
        val built = ActivityCombiner.combine(uri("watch"), listOf(watch, edge), now)
        val streams = built.streams!!
        assertTrue(streams.heartRate!!.all { it == 140 }, "the bike computer's strap has more heart rate")
        assertTrue(streams.power!!.all { it == 210 })
        assertEquals(140, built.record.avgHeartRate)
        assertEquals(210, built.record.avgPower)
        assertEquals("watch", built.record.title, "it starts from the activity combined into")
    }

    @Test
    fun aDeviceThatDiesHandsOverToTheNext() {
        val watch = member("watch", "09:00", "10:40", streams = { d -> d.copy(heartRate = d.time.map { 150 }) })
        val phone = member("phone", "10:38", "12:00", every = 3, streams = { d -> d.copy(heartRate = d.time.map { 110 }) })
        val streams = ActivityCombiner.combine(uri("watch"), listOf(watch, phone), now).streams!!
        fun hrAt(seconds: Int) = streams.heartRate!![streams.time.indexOf(seconds)]
        assertEquals(150, hrAt((10 * 60 + 39) * 60 - 9 * 3600), "the watch, while it recorded")
        assertEquals(110, hrAt((10 * 60 + 50) * 60 - 9 * 3600), "then the phone")
    }

    @Test
    fun aSkiDayWithLunchJoinsEndToEnd() {
        fun ski(rkey: String, from: String, to: String, meters: Int) =
            member(
                rkey,
                from,
                to,
                every = 10,
                record = { it.copy(distance = meters.toLong(), movingTime = it.elapsedTime) },
                streams = { d -> d.copy(distance = d.time.map { t -> (meters * 100L * t / d.time.last()).toInt() }) },
                sport = Sport.SKI,
            )
        val morning = ski("morning", "09:00", "11:45", 22_000)
        val afternoon = ski("afternoon", "13:00", "15:30", 18_000)
        val built = ActivityCombiner.combine(uri("morning"), listOf(afternoon, morning), now)
        val streams = built.streams!!
        assertEquals(40_000 * 100, streams.distance!!.last(), "the afternoon carries on from 22 km")
        val lunch = (2 * 3600 + 45 * 60 + 1) until (4 * 3600)
        assertTrue(streams.time.none { it in lunch }, "nothing between 11:45 and 13:00")
        assertEquals(at("09:00"), built.record.startedAt)
        assertEquals((6 * 60 + 30) * 60L, built.record.elapsedTime, "lunch counts in elapsed time")
        assertEquals((5 * 60 + 15) * 60L, built.record.movingTime, "but not in moving time")
        assertEquals(40_000L, built.record.distance)
        assertEquals(2, streams.legs?.size)
        assertEquals(listOf(0, 4 * 3600), streams.legs?.map { it.start })
    }

    /**
     * The athlete's real ride, in shape: an Edge whose file spans 09:00 to 10:33 but which died at 09:34, saving 5 stray points at 10:33,
     * and a watch started at 09:57 that finished the ride at 10:26. Strava's merge of the two runs 09:00 to 10:26.
     */
    @Test
    fun aDeviceThatDiedIsCoveredOnlyWhileItRecorded() {
        val start = at("09:00")
        val died = 34 * 60
        val stray = (93 * 60 until 93 * 60 + 5).toList()
        val edgeTimes = (0..died).toList() + stray
        val edge =
            CombineMember(
                uri("edge"),
                ActivityRecord(
                    sport = Sport.RIDE,
                    startedAt = start,
                    elapsedTime = (93 * 60 + 4).toLong(),
                    createdAt = start,
                    movingTime = 1957,
                    distance = 5234,
                    elevationGain = 224,
                    device = "Edge 840",
                ),
                ActivityStreamData(time = edgeTimes, distance = edgeTimes.map { minOf(it, died) * 523_400 / died }),
                edgeTimes.map { point(start + it.seconds, it) },
            )
        val watch =
            member(
                "watch",
                "09:57",
                "10:26",
                record = { it.copy(movingTime = 1734, distance = 8129, elevationGain = 85, device = "Fenix 7s") },
                streams = { d -> d.copy(distance = d.time.map { (812_900L * it / d.time.last()).toInt() }) },
                track = true,
            )

        assertEquals(
            listOf(listOf(uri("edge")), listOf(uri("watch"))),
            ActivityCombiner.groups(listOf(edge, watch)).map { g -> g.map { it.uri } },
        )
        val built = ActivityCombiner.combine(uri("edge"), listOf(edge, watch), now)
        val record = built.record
        assertEquals((86 * 60).toLong(), record.elapsedTime, "09:00 to 10:26: the stray points at 10:33 aren't part of the ride")
        assertEquals(1957L + 1734, record.movingTime)
        assertEquals(5234L + 8129, record.distance)
        assertEquals(224L + 85, record.elevationGain)
        assertEquals(listOf(0 to died, 57 * 60 to 29 * 60), built.streams?.legs?.map { it.start to it.elapsed })
        assertTrue(built.streams!!.time.none { it > 86 * 60 }, "no samples after the watch stopped")
        assertEquals(2, CombinedRoute.segments(built.route).size, "the map breaks between the Edge dying and the watch starting")
    }

    @Test
    fun aNullSampleFallsThroughToTheNextMember() {
        val watch =
            member("watch", "09:00", "09:10", streams = { d -> d.copy(heartRate = d.time.map { t -> if (t in 100..200) null else 150 }) })
        val edge = member("edge", "09:00", "09:10", every = 2, streams = { d -> d.copy(heartRate = d.time.map { 100 }) })
        val streams = ActivityCombiner.combine(uri("watch"), listOf(watch, edge), now).streams!!
        assertEquals(100, streams.heartRate!![streams.time.indexOf(150)])
        assertEquals(150, streams.heartRate!![streams.time.indexOf(300)])
    }

    @Test
    fun twoDevicesDontDoubleTheDistance() {
        fun ride(rkey: String, every: Int) =
            member(
                rkey,
                "09:00",
                "11:00",
                every = every,
                record = { it.copy(distance = 50_000, movingTime = 7000) },
                streams = { d -> d.copy(distance = d.time.map { t -> (5_000_000L * t / 7200).toInt() }) },
            )
        val built = ActivityCombiner.combine(uri("watch"), listOf(ride("watch", 1), ride("edge", 2)), now)
        assertTrue(abs(built.record.distance!! - 50_000) < 100, "about 50 km: ${built.record.distance}")
        assertEquals(7000L, built.record.movingTime)
    }

    @Test
    fun lapsComeFromTheMemberThatHasThem() {
        val watch = member("watch", "09:00", "10:00", streams = { d -> d.copy(heartRate = d.time.map { 120 }) })
        val laps = (0 until 5).map { Lap(start = it * 720, elapsed = 720) }
        val edge = member("edge", "09:00", "10:00", streams = { d -> d.copy(laps = laps) })
        val streams = ActivityCombiner.combine(uri("watch"), listOf(watch, edge), now).streams!!
        assertEquals(laps, streams.laps)
        assertNull(streams.legs)
    }

    @Test
    fun threeShuttleRunsAreThreeLegs() {
        val runs =
            listOf("10:00" to "10:20", "10:45" to "11:05", "11:30" to "11:50").mapIndexed { i, (from, to) ->
                member(
                    "run$i",
                    from,
                    to,
                    record = { it.copy(distance = 4000) },
                    streams = { d -> d.copy(speed = d.time.map { 3000 }) },
                    track = true,
                )
            }
        val built = ActivityCombiner.combine(uri("run0"), runs, now)
        val legs = built.streams!!.legs!!
        assertEquals(3, legs.size)
        assertEquals(listOf(4000, 4000, 4000), legs.map { it.distance })
        assertEquals(listOf(0, 45 * 60, 90 * 60), legs.map { it.start })
        val segments = CombinedRoute.segments(built.route)
        assertEquals(3, segments.size)
        assertEquals(3, Regex("<trkseg>").findAll(Gpx.writeSegments("Shuttle day", Sport.RIDE, segments)).count())
        val line = RouteTrim.Known(PrivacySettings.NONE, revision = null).segmentedPolylineOf(built.route)!!
        assertEquals(2, line.gaps?.size, "the map line breaks twice")
    }

    @Test
    fun diveGasSwitchesMoveOntoTheCombinedStart() {
        val fit =
            member(
                "fit",
                "10:00",
                "10:45",
                record = { it.copy(dive = ActivityDive(maxDepth = 3000)) },
                streams = { d -> d.copy(depth = d.time.map { 1000 }) },
                sport = Sport.DIVE,
            )
        val uddf =
            member(
                "uddf",
                "10:01",
                "10:44",
                every = 10,
                streams = { d -> d.copy(gasSwitches = listOf(GasSwitch(0, 21, 0), GasSwitch(1200, 50, 0))) },
                sport = Sport.DIVE,
            )
        val built = ActivityCombiner.combine(uri("fit"), listOf(fit, uddf), now)
        assertEquals(listOf(GasSwitch(60, 21, 0), GasSwitch(1260, 50, 0)), built.streams!!.gasSwitches)
        assertEquals(3000, built.record.dive?.maxDepth)
    }

    @Test
    fun theRecordListsItsMembersAndRankings() {
        val watch = member("watch", "09:00", "10:00", streams = { d -> d.copy(heartRate = d.time.map { 120 }) }, track = true)
        val edge = member("edge", "09:00", "10:00", every = 2, streams = { d -> d.copy(power = d.time.map { 200 }) })
        val combined = ActivityCombiner.combine(uri("edge"), listOf(watch, edge), now).record.combined!!
        assertEquals(setOf(uri("watch"), uri("edge")), combined.members.toSet())
        assertEquals(listOf(uri("watch")), combined.sourcesOf(StreamChannel.HEART_RATE))
        assertEquals(listOf(uri("edge")), combined.sourcesOf(StreamChannel.POWER))
        assertEquals(listOf(uri("watch")), combined.sourcesOf(CombinedChannel.POSITION))
    }

    @Test
    fun theTrackTakesTheBestMembersPointsAndTheOthersOnlyWhereItHasNone() {
        val start = at("09:00")
        fun track(rkey: String, seconds: Iterable<Int>, lat: Double) =
            CombineMember(
                uri(rkey),
                ActivityRecord(sport = Sport.RIDE, startedAt = start, elapsedTime = 600, createdAt = start),
                track = seconds.map { LocationSample(lat, 7.0, null, null, null, start + it.seconds) },
            )
        val edge = track("edge", (0..600).filterNot { it in 200..440 }, 46.0)
        val watch = track("watch", 0..600, 47.0)
        val route = CombinedRoute.build(listOf(edge, watch), listOf(uri("edge"), uri("watch")))
        assertTrue(
            route.filter { it.latitude == 47.0 }.all { (it.time - start).inWholeSeconds in 200..440 },
            "the watch fills the gap only",
        )
        // The edge's points at 199 s and 441 s cover the watch's within 5 s of them.
        assertEquals(360 + (205..435).count(), route.size)
    }

    @Test
    fun aGroupStartingInAPrivacyZoneIsTrimmedOnItsOwn() {
        val start = at("10:00")
        fun run(from: Int, lat: Double) =
            (0..300 step 10).map { LocationSample(lat + it * 1e-4, 7.0, null, null, null, start + (from + it).seconds) }
        val route = run(0, 46.0) + run(1800, 47.0)
        val home = PrivacyZone(latitudeE7 = 470_000_000, longitudeE7 = 70_000_000, radiusMeters = 200)
        val trimmed = RouteTrim.Known(PrivacySettings(listOf(home)), revision = null).segmentedPolylineOf(route)!!
        val points = TrackMath.decodePolyline(trimmed.polyline)
        assertEquals(1, trimmed.gaps?.size, "two pieces")
        val pieces = SegmentedPolyline.pieces(points, trimmed.gaps)
        assertTrue(pieces[1].none { (lat, _) -> lat < 47.0018 }, "the second run's start near home is hidden")
        assertTrue(pieces[0].any { (lat, _) -> lat < 46.0001 }, "the first run's start isn't in a zone")
    }

    @Test
    fun polylinePiecesSplitAtTheirGaps() {
        val points = (0 until 9).toList()
        assertEquals(listOf(listOf(0, 1, 2), listOf(3, 4, 5, 6), listOf(7, 8)), SegmentedPolyline.pieces(points, listOf(2, 6)))
        assertEquals(listOf(points), SegmentedPolyline.pieces(points, null))
    }

    @Test
    fun detailCategoriesTakeTheNarrowestMembers() {
        val tiers =
            ActivityCombiner.narrowestTiers(
                Visibility.PUBLIC,
                listOf(mapOf(DetailCategory.HEART_RATE to Visibility.SELF), mapOf(DetailCategory.HEART_RATE to Visibility.PUBLIC)),
            )
        assertEquals(Visibility.SELF, tiers[DetailCategory.HEART_RATE])
        assertEquals(Visibility.PUBLIC, tiers[DetailCategory.POWER])
    }
}
