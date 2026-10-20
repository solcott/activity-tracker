package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.DetailsView
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.Leg
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import io.github.solcott.activitytracker.lexicon.app.mergeStreams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class DetailCategoryTest {
    @Test
    fun everyRecordFieldHasOneCategoryOrIsCore() {
        val names = ActivityRecord.serializer().descriptor.let { d -> (0 until d.elementsCount).map { d.getElementName(it) } }
        val categorized = DetailCategory.SUMMARY_FIELDS.map { it.name }
        assertEquals(categorized.size, categorized.toSet().size, "a summary field in two categories")
        assertEquals(emptySet(), categorized.toSet() intersect DetailCategory.CORE_RECORD_FIELDS)
        assertEquals(emptySet(), names.toSet() - categorized.toSet() - DetailCategory.CORE_RECORD_FIELDS, "uncategorized record fields")
        assertTrue(DetailCategory.SUMMARY_FIELDS.all { it.category in DetailCategory.all })
    }

    @Test
    fun detailsRecordMirrorsTheSummaryFields() {
        val names = ActivityDetailsRecord.serializer().descriptor.let { d -> (0 until d.elementsCount).map { d.getElementName(it) } }
        assertEquals(DetailCategory.SUMMARY_FIELDS.map { it.name }.toSet() + setOf("activity", "createdAt", "\$type"), names.toSet())
    }

    @Test
    fun everyChannelHasOneCategoryOrIsCore() {
        val channels = ActivityStreamData.INT_CHANNELS.map { it.token } + STRUCTURED_CHANNELS
        val categorized = DetailCategory.CHANNELS.values.flatten()
        assertEquals(categorized.size, categorized.toSet().size, "a channel in two categories")
        assertEquals(emptySet(), categorized.toSet() intersect DetailCategory.CORE_CHANNELS)
        assertEquals(emptySet(), channels.toSet() - categorized.toSet() - DetailCategory.CORE_CHANNELS, "uncategorized channels")
    }

    @Test
    fun runningFormIsCadenceWithoutCadenceItself() {
        assertEquals(
            setOf(
                StreamChannel.VERTICAL_OSCILLATION,
                StreamChannel.STANCE_TIME,
                StreamChannel.STEP_LENGTH,
                StreamChannel.VERTICAL_RATIO,
                StreamChannel.STANCE_TIME_BALANCE,
                StreamChannel.LEG_SPRING_STIFFNESS,
            ),
            DetailCategory.RUNNING_FORM_CHANNELS,
        )
    }

    @Test
    fun everyLapAndLegFieldHasOneCategoryOrIsCore() {
        val categorized = DetailCategory.LAP_FIELDS.values.flatten()
        assertEquals(categorized.size, categorized.toSet().size)
        for (descriptor in listOf(Lap.serializer().descriptor, Leg.serializer().descriptor)) {
            val names = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.toSet()
            assertEquals(emptySet(), names - categorized.toSet() - DetailCategory.CORE_LAP_FIELDS, "uncategorized ${descriptor.serialName}")
        }
    }

    @Test
    fun localMidnightUsesTheOffset() {
        // 06:12 at UTC-7 is 13:12Z; local midnight is 07:00Z.
        assertEquals(
            Instant.parse("2026-03-04T07:00:00Z"),
            DetailCategory.localMidnight(Instant.parse("2026-03-04T13:12:00Z"), utcOffset = -420),
        )
        // 00:30 at UTC+9 is the previous day in UTC.
        assertEquals(
            Instant.parse("2026-03-03T15:00:00Z"),
            DetailCategory.localMidnight(Instant.parse("2026-03-03T15:30:00Z"), utcOffset = 540),
        )
        assertEquals(Instant.parse("2026-03-04T00:00:00Z"), DetailCategory.localMidnight(Instant.parse("2026-03-04T13:12:00Z"), null))
    }

    @Test
    fun splitAndOverlayRoundTrip() {
        val categories =
            mapOf(
                DetailCategory.HEART_RATE to Visibility.SELF,
                DetailCategory.POWER to Visibility.FOLLOWERS,
                DetailCategory.START_TIME to Visibility.SELF,
            )
        val stored = DetailCategory.recordFor(RIDE, Visibility.PUBLIC, categories)
        assertNull(stored.avgHeartRate)
        assertNull(stored.avgPower)
        assertEquals(150L, stored.avgSpeed)
        assertEquals(Instant.parse("2026-03-04T07:00:00Z"), stored.startedAt)

        val details = DetailCategory.detailsFor(RIDE, URI, Visibility.PUBLIC, categories)
        assertEquals(setOf(Visibility.FOLLOWERS, Visibility.SELF), details.keys)
        assertEquals(240, details.getValue(Visibility.FOLLOWERS).avgPower)
        assertNull(details.getValue(Visibility.FOLLOWERS).avgHeartRate)
        assertEquals(RIDE.startedAt, details.getValue(Visibility.SELF).startedAt)

        val ordered = Visibility.all.mapNotNull { details[it] }
        assertEquals(RIDE, effectiveRecord(stored, ordered))
        // A follower gets only the followers tier.
        val follower = effectiveRecord(stored, listOfNotNull(details[Visibility.FOLLOWERS]))
        assertEquals(240, follower.avgPower)
        assertNull(follower.avgHeartRate)
    }

    @Test
    fun nothingHiddenMeansNoDetails() {
        assertEquals(emptyMap(), DetailCategory.detailsFor(RIDE, URI, Visibility.FOLLOWERS, emptyMap()))
        assertEquals(RIDE, DetailCategory.recordFor(RIDE, Visibility.FOLLOWERS, mapOf(DetailCategory.POWER to Visibility.PUBLIC)))
    }

    @Test
    fun streamsSplitByTierAndMergeBack() {
        val categories = mapOf(DetailCategory.HEART_RATE to Visibility.SELF)
        val parts = DetailCategory.streamsFor(STREAMS, Visibility.PUBLIC, categories)
        assertEquals(setOf(Visibility.PUBLIC, Visibility.SELF), parts.keys)
        val public = parts.getValue(Visibility.PUBLIC)
        assertNull(public.heartRate)
        assertEquals(STREAMS.speed, public.speed)
        assertEquals(STREAMS.altitude, public.altitude)
        assertNull(public.laps!!.first().avgHeartRate)
        assertEquals(3, public.laps.first().distance)
        val self = parts.getValue(Visibility.SELF)
        assertEquals(listOf(StreamChannel.HEART_RATE, StreamChannel.LAPS), self.channels)
        assertEquals(Lap(start = 0, elapsed = 60, avgHeartRate = 140, maxHeartRate = 160), self.laps!!.first())

        assertEquals(STREAMS, mergeStreams(Visibility.all.mapNotNull { parts[it] }))
        assertEquals(public, mergeStreams(listOf(public)))
    }

    @Test
    fun allAtOneTierIsOneRecord() {
        val parts = DetailCategory.streamsFor(STREAMS, Visibility.MUTUALS, DetailCategory.all.associateWith { Visibility.PUBLIC })
        assertEquals(mapOf(Visibility.MUTUALS to STREAMS), parts)
    }

    @Test
    fun mergeAlignsDifferentTimes() {
        val wide = ActivityStreamData(time = listOf(0, 1, 2, 3), speed = listOf(1, 2, 3, 4))
        val narrow = ActivityStreamData(time = listOf(0, 2), heartRate = listOf(100, 120))
        assertEquals(listOf(100, 100, 120, 120), mergeStreams(listOf(wide, narrow))!!.heartRate)
    }

    @Test
    fun currentTiersOfAnOldActivity() {
        val tiers =
            DetailCategory.currentTiers(
                Visibility.PUBLIC,
                RIDE,
                details = emptyList(),
                parts = listOf(Visibility.SELF to STREAMS),
            )
        assertEquals(Visibility.SELF, tiers[DetailCategory.HEART_RATE])
        assertEquals(Visibility.SELF, tiers[DetailCategory.SPEED])
        assertEquals(Visibility.PUBLIC, tiers[DetailCategory.CALORIES])
        assertEquals(Visibility.PUBLIC, tiers[DetailCategory.START_TIME])
        assertNull(tiers[DetailCategory.DIVE])
    }

    @Test
    fun currentTiersOfASplitActivity() {
        val categories = mapOf(DetailCategory.HEART_RATE to Visibility.SELF, DetailCategory.START_TIME to Visibility.MUTUALS)
        val details = DetailCategory.detailsFor(RIDE, URI, Visibility.PUBLIC, categories).map { (tier, d) -> DetailsView(tier, d) }
        val parts = DetailCategory.streamsFor(STREAMS, Visibility.PUBLIC, categories).toList()
        val tiers =
            DetailCategory.currentTiers(Visibility.PUBLIC, DetailCategory.recordFor(RIDE, Visibility.PUBLIC, categories), details, parts)
        assertEquals(Visibility.SELF, tiers[DetailCategory.HEART_RATE])
        assertEquals(Visibility.MUTUALS, tiers[DetailCategory.START_TIME])
        assertEquals(Visibility.PUBLIC, tiers[DetailCategory.POWER])
    }

    @Test
    fun currentTiersFromServedPartsOfAnOldActivity() {
        val view =
            ActivityView(
                uri = URI,
                cid = Cid("bafyrei"),
                author = ProfileViewBasic(Did("did:plc:alice"), Handle("alice.test")),
                record = RIDE,
                likeCount = 0,
                commentCount = 0,
                indexedAt = RIDE.createdAt,
            )
        // An AppView from before categories: no parts, one visibility.
        val old = GetActivityStreams.Output(URI, Visibility.SELF, listOf(StreamChannel.HEART_RATE, StreamChannel.SPEED), STREAMS)
        val tiers = DetailCategory.currentTiers(Visibility.PUBLIC, view, old)
        assertEquals(Visibility.SELF, tiers[DetailCategory.HEART_RATE])
        assertEquals(Visibility.SELF, tiers[DetailCategory.SPEED])
        assertEquals(Visibility.PUBLIC, tiers[DetailCategory.POWER])
        assertEquals(Visibility.PUBLIC, tiers[DetailCategory.CALORIES])

        val split =
            old.copy(
                visibility = Visibility.PUBLIC,
                parts =
                    listOf(
                        GetActivityStreams.StreamsPart(Visibility.PUBLIC, listOf(StreamChannel.SPEED)),
                        GetActivityStreams.StreamsPart(Visibility.FOLLOWERS, listOf(StreamChannel.HEART_RATE)),
                    ),
            )
        val withDetails =
            view.copy(details = listOf(DetailsView(Visibility.MUTUALS, ActivityDetailsRecord(URI, RIDE.createdAt, calories = 1))))
        val splitTiers = DetailCategory.currentTiers(Visibility.PUBLIC, withDetails, split)
        assertEquals(Visibility.FOLLOWERS, splitTiers[DetailCategory.HEART_RATE])
        assertEquals(Visibility.PUBLIC, splitTiers[DetailCategory.SPEED])
        assertEquals(Visibility.PUBLIC, splitTiers[DetailCategory.CALORIES], "on the record and a details record: the widest")
        assertEquals(
            Visibility.MUTUALS,
            DetailCategory.currentTiers(Visibility.PUBLIC, withDetails.copy(record = RIDE.copy(calories = null)), split)[
                    DetailCategory.CALORIES],
        )
    }

    @Test
    fun effectiveRecordOfAViewLaysItsDetailsOver() {
        val stored = DetailCategory.recordFor(RIDE, Visibility.PUBLIC, mapOf(DetailCategory.HEART_RATE to Visibility.SELF))
        val details = DetailCategory.detailsFor(RIDE, URI, Visibility.PUBLIC, mapOf(DetailCategory.HEART_RATE to Visibility.SELF))
        val view =
            ActivityView(
                uri = URI,
                cid = Cid("bafyrei"),
                author = ProfileViewBasic(Did("did:plc:alice"), Handle("alice.test")),
                record = stored,
                likeCount = 0,
                commentCount = 0,
                indexedAt = RIDE.createdAt,
            )
        assertNull(view.effectiveRecord().avgHeartRate)
        assertEquals(RIDE, view.copy(details = details.map { (t, d) -> DetailsView(t, d) }).effectiveRecord())
    }

    private companion object {
        val STRUCTURED_CHANNELS =
            listOf(
                StreamChannel.GEARS,
                StreamChannel.GAS_SWITCHES,
                StreamChannel.DIVE_ALERTS,
                StreamChannel.TANK_PRESSURES,
                StreamChannel.LAPS,
                StreamChannel.LENGTHS,
                StreamChannel.SETS,
                StreamChannel.LEGS,
            )
        val URI = AtUri("at://did:plc:alice/${Nsid.FEED_ACTIVITY}/3k")
        val RIDE =
            ActivityRecord(
                sport = "ride",
                startedAt = Instant.parse("2026-03-04T13:12:00Z"),
                elapsedTime = 3600,
                createdAt = Instant.parse("2026-03-04T15:00:00Z"),
                distance = 30000,
                avgSpeed = 150,
                avgHeartRate = 150,
                maxHeartRate = 180,
                avgPower = 240,
                calories = 800,
                utcOffset = -420,
            )
        val STREAMS =
            ActivityStreamData(
                time = listOf(0, 1, 2),
                altitude = listOf(100, 101, 102),
                speed = listOf(1, 2, 3),
                heartRate = listOf(120, 130, 140),
                laps = listOf(Lap(start = 0, elapsed = 60, distance = 3, avgSpeed = 5, avgHeartRate = 140, maxHeartRate = 160)),
            )
    }
}
