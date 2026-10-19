package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.activity.ActivityCombiner
import io.github.solcott.activitytracker.data.activity.CombineMember
import io.github.solcott.activitytracker.data.activity.DataKind
import io.github.solcott.activitytracker.data.activity.DataKinds
import io.github.solcott.activitytracker.data.activity.KindSource
import io.github.solcott.activitytracker.data.activity.SourceChoice
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ChannelSource
import io.github.solcott.activitytracker.lexicon.app.CombinedChannel
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** `feed/combined-sources`: the kinds of data a combined activity's athlete chooses a source for or leaves out. */
class DataKindsTest {
    private val now = Instant.parse("2026-10-14T20:00:00Z")
    private val start = Instant.parse("2026-10-14T09:00:00Z")
    private val time = (0..3600).toList()

    private fun uri(rkey: String) = AtUri("at://did:plc:alice/${Nsid.FEED_ACTIVITY}/$rkey")

    private fun member(
        rkey: String,
        streams: (ActivityStreamData) -> ActivityStreamData,
        track: Boolean = true,
        record: (ActivityRecord) -> ActivityRecord = { it },
        sport: String = Sport.RIDE,
    ) =
        CombineMember(
            uri(rkey),
            record(ActivityRecord(sport = sport, startedAt = start, elapsedTime = 3600, createdAt = start, title = rkey)),
            streams(ActivityStreamData(time = time)),
            if (track) time.map { t -> LocationSample(46.0 + t * 1e-5, 7.0, 1500.0, null, null, start + t.seconds) } else null,
        )

    /** A watch with GPS and wrist heart rate. */
    private val watch = member("watch", { d -> d.copy(heartRate = d.time.map { 150 }) })

    /** A bike computer with GPS, a chest strap, a power meter with balance, and barometric altitude, logging every other second. */
    private val edge =
        member(
            "edge",
            { d ->
                val every = d.time.filter { it % 2 == 0 }
                d.copy(
                    time = every,
                    heartRate = every.map { 140 },
                    power = every.map { 250 },
                    leftRightBalance = every.map { 50 },
                    altitude = every.map { 1500 },
                )
            },
            record = { it.copy(avgPower = 250, normalizedPower = 260, work = 900_000) },
        )

    private val members = listOf(watch, edge)

    @Test
    fun everyKindAnyMemberRecordedIsListedAndIncluded() {
        val listed = DataKinds.of(members, emptyList())
        assertEquals(listOf(DataKind.Map, DataKind.Elevation, DataKind.HeartRate, DataKind.Power), listed.map { it.kind })
        val kinds = listed.associateBy { it.kind }
        assertEquals(setOf(uri("watch"), uri("edge")), kinds.getValue(DataKind.Map).members.toSet())
        assertEquals(setOf(uri("watch"), uri("edge")), kinds.getValue(DataKind.HeartRate).members.toSet())
        assertEquals(KindSource(DataKind.Power, listOf(uri("edge")), uri("edge")), kinds.getValue(DataKind.Power))
        assertEquals(KindSource(DataKind.Elevation, listOf(uri("edge")), uri("edge")), kinds.getValue(DataKind.Elevation))
        assertTrue(kinds.values.none { it.excluded })
    }

    @Test
    fun distanceAndSpeedAreListedWithBothMembersWhenBothRecordThem() {
        val runner = member("watch", { d -> d.copy(distance = d.time.map { it * 300 }, speed = d.time.map { 3000 }) })
        val pod = member("pod", { d -> d.copy(speed = d.time.map { 3100 }) }, track = false)
        val kind = DataKinds.of(listOf(runner, pod), emptyList()).single { it.kind == DataKind.DistanceSpeed }
        assertEquals(setOf(uri("watch"), uri("pod")), kind.members.toSet())
    }

    @Test
    fun aDivePairListsDive() {
        fun computer(rkey: String) =
            member(
                rkey,
                { d -> d.copy(depth = d.time.map { 1000 }) },
                track = false,
                record = { it.copy(dive = ActivityDive(maxDepth = 1000)) },
                sport = Sport.DIVE,
            )
        val kinds = DataKinds.of(listOf(computer("a"), computer("b")), emptyList())
        assertEquals(listOf(DataKind.Dive), kinds.map { it.kind })
        assertEquals(2, kinds.single().members.size)
    }

    @Test
    fun aKindStoredWithNoMembersIsListedAsExcluded() {
        val stored = DataKinds.choose(members, emptyList(), DataKind.Power, SourceChoice.Exclude)
        val power = DataKinds.of(members, stored).single { it.kind == DataKind.Power }
        assertTrue(power.excluded)
        assertEquals(listOf(uri("edge")), power.members)
    }

    @Test
    fun excludingPowerBuildsNoPowerChannelsOrFigures() {
        val stored = DataKinds.choose(members, emptyList(), DataKind.Power, SourceChoice.Exclude)
        val built = ActivityCombiner.combine(uri("watch"), members, now, stored)
        val streams = assertNotNull(built.streams)
        assertNull(streams.power)
        assertNull(streams.leftRightBalance)
        assertNull(built.record.avgPower)
        assertNull(built.record.maxPower)
        assertNull(built.record.normalizedPower)
        assertNull(built.record.work)
        assertNotNull(streams.heartRate)
        assertNotNull(streams.altitude)
    }

    @Test
    fun anEmptyListIsKeptRatherThanRefilledByTheDefault() {
        val stored = DataKinds.choose(members, emptyList(), DataKind.Power, SourceChoice.Exclude)
        val combined = ActivityCombiner.combine(uri("watch"), members, now, stored).record.combined!!
        assertEquals(emptyList(), combined.sourcesOf(StreamChannel.POWER))
        assertEquals(emptyList(), combined.sourcesOf(StreamChannel.LEFT_RIGHT_BALANCE))
        // Rebuilt from what it stored, it stays out.
        assertNull(ActivityCombiner.combine(uri("watch"), members, now, combined.sources).streams!!.power)
    }

    @Test
    fun aChannelWithNoStoredEntryGetsTheDefault() {
        val stored = listOf(ChannelSource(StreamChannel.HEART_RATE, listOf(uri("edge"))))
        val rankings = ActivityCombiner.rankings(members, stored)
        assertEquals(listOf(uri("edge"), uri("watch")), rankings[StreamChannel.HEART_RATE])
        assertEquals(listOf(uri("edge")), rankings[StreamChannel.POWER])
        // A stored list naming only members that are gone falls back to the default too, unlike an empty one.
        assertEquals(
            listOf(uri("edge")),
            ActivityCombiner.rankings(members, listOf(ChannelSource(StreamChannel.POWER, listOf(uri("gone")))))[StreamChannel.POWER],
        )
    }

    @Test
    fun excludingTheMapDropsTheRouteAndHeartRateKeepsCalories() {
        val withCalories = members.map { m -> m.copy(record = m.record.copy(calories = 500)) }
        val noMap = DataKinds.choose(withCalories, emptyList(), DataKind.Map, SourceChoice.Exclude)
        val stored = DataKinds.choose(withCalories, noMap, DataKind.HeartRate, SourceChoice.Exclude)
        val built = ActivityCombiner.combine(uri("watch"), withCalories, now, stored)
        assertTrue(built.route.isEmpty())
        assertNull(built.streams!!.heartRate)
        assertNull(built.record.avgHeartRate)
        assertEquals(500, built.record.calories)
        assertEquals(
            setOf(CombinedChannel.POSITION, StreamChannel.HEART_RATE),
            stored.filter { it.members.isEmpty() }.map { it.channel }.toSet(),
        )
    }

    @Test
    fun excludingDistanceAndSpeedLeavesMovingTimeAsElapsed() {
        val runner = member("watch", { d -> d.copy(distance = d.time.map { it * 300 }, speed = d.time.map { 3000 }) })
        val pod = member("pod", { d -> d.copy(speed = d.time.map { 3100 }, heartRate = d.time.map { 150 }) }, track = false)
        val stored = DataKinds.choose(listOf(runner, pod), emptyList(), DataKind.DistanceSpeed, SourceChoice.Exclude)
        val record = ActivityCombiner.combine(uri("watch"), listOf(runner, pod), now, stored).record
        assertNull(record.distance)
        assertNull(record.avgSpeed)
        assertEquals(record.elapsedTime, record.movingTime)
    }

    @Test
    fun choosingAMemberMovesItFirstForEveryChannelOfTheKind() {
        val stored = DataKinds.choose(members, emptyList(), DataKind.HeartRate, SourceChoice.Member(uri("edge")))
        assertEquals(listOf(ChannelSource(StreamChannel.HEART_RATE, listOf(uri("edge"), uri("watch")))), stored)
        val built = ActivityCombiner.combine(uri("watch"), members, now, stored)
        assertEquals(140, built.record.avgHeartRate)
    }

    @Test
    fun choosingPowerMovesBalanceAndPedalChannelsToo() {
        val crank =
            member(
                "crank",
                { d -> d.copy(power = d.time.map { 200 }, leftRightBalance = d.time.map { 48 }, pedalSmoothnessLeft = d.time.map { 20 }) },
                track = false,
            )
        val pedals =
            member(
                "pedals",
                { d -> d.copy(power = d.time.map { 210 }, leftRightBalance = d.time.map { 52 }, pedalSmoothnessLeft = d.time.map { 25 }) },
                track = false,
            )
        val all = listOf(crank, pedals)
        val stored = DataKinds.choose(all, emptyList(), DataKind.Power, SourceChoice.Member(uri("pedals")))
        val streams = assertNotNull(ActivityCombiner.combine(uri("crank"), all, now, stored).streams)
        assertTrue(streams.power!!.all { it == 210 })
        assertTrue(streams.leftRightBalance!!.all { it == 52 })
        assertTrue(streams.pedalSmoothnessLeft!!.all { it == 25 })
    }

    @Test
    fun includingAgainDropsTheKindsEntriesSoTheDefaultApplies() {
        val excluded = DataKinds.choose(members, emptyList(), DataKind.Power, SourceChoice.Exclude)
        val again = DataKinds.choose(members, excluded, DataKind.Power, SourceChoice.Default)
        assertTrue(again.none { it.channel in DataKind.Power.channels })
        assertEquals(250, ActivityCombiner.combine(uri("watch"), members, now, again).record.avgPower)
    }

    @Test
    fun whereTheChosenMemberLostGpsTheNextFillsIn() {
        // The bike computer lost GPS for four minutes under trees, which the watch recorded.
        val trees = 1200..1440
        val lossy = edge.copy(track = edge.track!!.filterIndexed { t, _ -> t !in trees })
        val all = listOf(watch, lossy)
        val stored = DataKinds.choose(all, emptyList(), DataKind.Map, SourceChoice.Member(uri("edge")))
        val route = ActivityCombiner.combine(uri("watch"), all, now, stored).route
        val fromWatch = route.filter { p -> watch.track!!.any { it.time == p.time } && lossy.track!!.none { it.time == p.time } }
        assertTrue(fromWatch.all { ((it.time - start).inWholeSeconds.toInt()) in trees })
        assertTrue(fromWatch.isNotEmpty())
    }

    @Test
    fun choosingAMemberForAnExcludedKindIncludesItAgain() {
        val excluded = DataKinds.choose(members, emptyList(), DataKind.HeartRate, SourceChoice.Exclude)
        val stored = DataKinds.choose(members, excluded, DataKind.HeartRate, SourceChoice.Member(uri("watch")))
        assertEquals(listOf(uri("watch"), uri("edge")), stored.single { it.channel == StreamChannel.HEART_RATE }.members)
    }
}
