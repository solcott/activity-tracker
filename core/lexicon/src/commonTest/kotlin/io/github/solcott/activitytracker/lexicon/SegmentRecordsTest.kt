package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.CheckSegment
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.GetSegmentLeaderboard
import io.github.solcott.activitytracker.lexicon.app.GetSegmentMayors
import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardBracketsRecord
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.LeaderboardGender
import io.github.solcott.activitytracker.lexicon.app.LeaderboardPeriod
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.MayorPeriod
import io.github.solcott.activitytracker.lexicon.app.NotRanked
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentPrefsRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentStarRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackData
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackPiece
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.SegmentViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentViewerState
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `feed.segment`, its views, `feed.segmentTrack`, its data blob and `actor.segmentPrefs` as `AtJson` writes and reads them. */
class SegmentRecordsTest {
    @Test
    fun segmentTrackAndPrefsRoundTrip() {
        val activity = AtUri.of(Did("did:plc:alice"), Nsid.FEED_ACTIVITY, "3l2abcdefgh22")
        val record =
            SegmentTrackRecord(
                activity = activity,
                createdAt = Instant.parse("2026-10-15T12:00:00Z"),
                zonesRevision = "3l2abcdefgh33",
                data = BlobRef(ref = BlobRef.Link("bafkreidata"), mimeType = SegmentTrackRecord.MIME_TYPE, size = 120),
            )
        val json = AtJson.encodeToJsonElement(SegmentTrackRecord.serializer(), record).jsonObject
        assertEquals(Nsid.FEED_SEGMENT_TRACK, json["\$type"]?.jsonPrimitive?.content)
        assertEquals(record, AtJson.decodeFromJsonElement(SegmentTrackRecord.serializer(), json))
        val bare =
            AtJson.decodeFromString(
                SegmentTrackRecord.serializer(),
                """{"${'$'}type":"${Nsid.FEED_SEGMENT_TRACK}","activity":"$activity","createdAt":"2026-10-15T12:00:00Z","later":1}""",
            )
        assertNull(bare.zonesRevision)

        val data =
            SegmentTrackData(
                listOf(
                    SegmentTrackPiece(lat = listOf(515_000_000), lng = listOf(-1_275_000), t = listOf(0L), alt = listOf(null)),
                    SegmentTrackPiece(lat = listOf(515_100_000), lng = listOf(-1_265_000), t = listOf(600L)),
                )
            )
        val dataJson = AtJson.encodeToJsonElement(SegmentTrackData.serializer(), data).jsonObject
        assertFalse("alt" in dataJson["pieces"]!!.jsonArray[1].jsonObject)
        assertEquals(data, AtJson.decodeFromJsonElement(SegmentTrackData.serializer(), dataJson))

        val prefs = SegmentPrefsRecord(matching = true, updatedAt = Instant.parse("2026-10-15T12:00:00Z"))
        val prefsJson = AtJson.encodeToJsonElement(SegmentPrefsRecord.serializer(), prefs).jsonObject
        assertEquals(Nsid.ACTOR_SEGMENT_PREFS, prefsJson["\$type"]?.jsonPrimitive?.content)
        assertEquals(prefs, AtJson.decodeFromJsonElement(SegmentPrefsRecord.serializer(), prefsJson))
    }

    @Test
    fun leaderboardBracketsRoundTrip() {
        val brackets =
            LeaderboardBracketsRecord(
                updatedAt = Instant.parse("2026-10-22T12:00:00Z"),
                ageGroup = LeaderboardAgeGroup.FROM_35,
                gender = LeaderboardGender.FEMALE,
            )
        val json = AtJson.encodeToJsonElement(LeaderboardBracketsRecord.serializer(), brackets).jsonObject
        assertEquals(Nsid.ACTOR_LEADERBOARD_BRACKETS, json["\$type"]?.jsonPrimitive?.content)
        assertEquals("35-44", json["ageGroup"]?.jsonPrimitive?.content)
        assertFalse("weightClass" in json)
        assertEquals(brackets, AtJson.decodeFromJsonElement(LeaderboardBracketsRecord.serializer(), json))
    }

    @Test
    fun segmentStarRoundTrip() {
        val segment = AtUri.of(Did("did:plc:bob"), Nsid.FEED_SEGMENT, "3l2segment22")
        val star = SegmentStarRecord(subject = StrongRef(segment, Cid("bafysegment")), createdAt = Instant.parse("2026-10-21T12:00:00Z"))
        val json = AtJson.encodeToJsonElement(SegmentStarRecord.serializer(), star).jsonObject
        assertEquals(Nsid.FEED_SEGMENT_STAR, json["\$type"]?.jsonPrimitive?.content)
        assertEquals(segment.value, json["subject"]!!.jsonObject["uri"]?.jsonPrimitive?.content)
        assertEquals(star, AtJson.decodeFromJsonElement(SegmentStarRecord.serializer(), json))
    }

    @Test
    fun segmentRoundTrip() {
        val alice = Did("did:plc:alice")
        val activity = AtUri.of(alice, Nsid.FEED_ACTIVITY, "3l2abcdefgh22")
        val record =
            SegmentRecord(
                name = "Hill climb",
                sport = SegmentSportGroup.RIDE,
                line = "_p~iF~ps|U_ulLnnqC",
                source = SegmentSource.ATHLETE,
                license = "CC0-1.0",
                createdAt = Instant.parse("2026-10-15T12:00:00Z"),
                derivedFrom = StrongRef(activity, Cid("bafyactivity")),
            )
        val json = AtJson.encodeToJsonElement(SegmentRecord.serializer(), record).jsonObject
        assertEquals(Nsid.FEED_SEGMENT, json["\$type"]?.jsonPrimitive?.content)
        assertEquals(record, AtJson.decodeFromJsonElement(SegmentRecord.serializer(), json))

        val bare =
            AtJson.decodeFromString(
                SegmentRecord.serializer(),
                """{"${'$'}type":"${Nsid.FEED_SEGMENT}","name":"Hill","sport":"ride","line":"_p~iF","source":"osm",""" +
                    """"license":"ODbL-1.0","createdAt":"2026-10-15T12:00:00Z","later":1}""",
            )
        assertNull(bare.derivedFrom)
        assertNull(bare.attribution)
        assertFalse("derivedFrom" in AtJson.encodeToJsonElement(SegmentRecord.serializer(), bare).jsonObject)
        assertFalse("attribution" in AtJson.encodeToJsonElement(SegmentRecord.serializer(), bare).jsonObject)

        val agency =
            record.copy(source = SegmentSource.AGENCY, license = "CC0-1.0", derivedFrom = null, attribution = "USDA Forest Service")
        val agencyJson = AtJson.encodeToJsonElement(SegmentRecord.serializer(), agency).jsonObject
        assertEquals("USDA Forest Service", agencyJson["attribution"]?.jsonPrimitive?.content)
        assertEquals(agency, AtJson.decodeFromJsonElement(SegmentRecord.serializer(), agencyJson))

        val uri = AtUri.of(alice, Nsid.FEED_SEGMENT, "3l2abcdefgh44")
        val effort =
            EffortView(
                segment = SegmentViewBasic(uri, record.name, record.sport, distanceMeters = 1_200),
                activity = activity,
                startOffset = 120,
                elapsed = 611,
                startedOn = "2026-10-15",
                best = true,
            )
        val view =
            SegmentView(
                uri = uri,
                cid = Cid("bafysegment"),
                creator = ProfileViewBasic(alice, Handle("alice.test")),
                record = record,
                distanceMeters = 1_200,
                viewer = SegmentViewerState(best = effort),
            )
        val output = GetSegment.Output(view)
        val outputJson = AtJson.encodeToJsonElement(GetSegment.Output.serializer(), output).jsonObject
        assertFalse("eBike" in outputJson["segment"]!!.jsonObject["viewer"]!!.jsonObject["best"]!!.jsonObject)
        assertEquals(output, AtJson.decodeFromJsonElement(GetSegment.Output.serializer(), outputJson))
        assertFalse("status" in outputJson["segment"]!!.jsonObject)
        assertFalse("duplicateOf" in outputJson["segment"]!!.jsonObject)
    }

    @Test
    fun statusesAndTheCheckRoundTrip() {
        val alice = Did("did:plc:alice")
        val uri = AtUri.of(alice, Nsid.FEED_SEGMENT, "3l2abcdefgh44")
        val record =
            SegmentRecord(
                "Hill climb",
                SegmentSportGroup.RIDE,
                "_p~iF~ps|U_ulLnnqC",
                SegmentSource.ATHLETE,
                "CC0-1.0",
                Instant.parse("2026-10-15T12:00:00Z"),
            )
        val view = SegmentView(uri, Cid("bafysegment"), ProfileViewBasic(alice, Handle("alice.test")), record, distanceMeters = 1_200)
        val effort =
            EffortView(
                SegmentViewBasic(uri, record.name, record.sport, 1_200),
                AtUri.of(alice, Nsid.FEED_ACTIVITY, "3l2abcdefgh22"),
                120,
                611,
                "2026-10-15",
            )
        assertEquals(SegmentStatus.ACTIVE, SegmentStatus.of(view.status))

        val original = AtUri.of(Did("did:plc:bob"), Nsid.FEED_SEGMENT, "3l2abcdefgh11")
        val duplicate = view.copy(status = SegmentStatus.DUPLICATE, duplicateOf = original, viewer = null)
        val checked = CheckSegment.Output(duplicates = listOf(duplicate), remaining = 8)
        val checkedJson = AtJson.encodeToJsonElement(CheckSegment.Output.serializer(), checked).jsonObject
        val duplicateJson = checkedJson["duplicates"]!!.jsonArray[0].jsonObject
        assertEquals(SegmentStatus.DUPLICATE, duplicateJson["status"]?.jsonPrimitive?.content)
        assertEquals(original.value, duplicateJson["duplicateOf"]?.jsonPrimitive?.content)
        assertEquals(checked, AtJson.decodeFromJsonElement(CheckSegment.Output.serializer(), checkedJson))

        val basic = effort.segment.copy(status = SegmentStatus.PROPOSED)
        assertEquals(
            basic,
            AtJson.decodeFromJsonElement(SegmentViewBasic.serializer(), AtJson.encodeToJsonElement(SegmentViewBasic.serializer(), basic)),
        )
        assertEquals(SegmentStatus.ACTIVE, SegmentStatus.of("someday"))
        assertEquals(SegmentStatus.ARCHIVED, SegmentStatus.of(SegmentStatus.ARCHIVED))

        val input = CheckSegment.Input(sport = SegmentSportGroup.RUN, line = record.line)
        assertEquals(
            input,
            AtJson.decodeFromJsonElement(
                CheckSegment.Input.serializer(),
                AtJson.encodeToJsonElement(CheckSegment.Input.serializer(), input),
            ),
        )
    }

    @Test
    fun leaderboardFieldsRoundTrip() {
        val alice = Did("did:plc:alice")
        val activity = AtUri.of(alice, Nsid.FEED_ACTIVITY, "3l2abcdefgh22")
        val track =
            SegmentTrackRecord(
                activity = activity,
                createdAt = Instant.parse("2026-10-15T12:00:00Z"),
                data = BlobRef(ref = BlobRef.Link("bafkreidata"), mimeType = SegmentTrackRecord.MIME_TYPE, size = 120),
                excludeFromLeaderboards = true,
            )
        val trackJson = AtJson.encodeToJsonElement(SegmentTrackRecord.serializer(), track).jsonObject
        assertEquals("true", trackJson["excludeFromLeaderboards"]?.jsonPrimitive?.content)
        assertEquals(track, AtJson.decodeFromJsonElement(SegmentTrackRecord.serializer(), trackJson))
        val unmarked = track.copy(excludeFromLeaderboards = null)
        assertFalse("excludeFromLeaderboards" in AtJson.encodeToJsonElement(SegmentTrackRecord.serializer(), unmarked).jsonObject)

        val prefs = SegmentPrefsRecord(matching = true, updatedAt = Instant.parse("2026-10-15T12:00:00Z"), hideFromLeaderboards = true)
        val prefsJson = AtJson.encodeToJsonElement(SegmentPrefsRecord.serializer(), prefs).jsonObject
        assertEquals(prefs, AtJson.decodeFromJsonElement(SegmentPrefsRecord.serializer(), prefsJson))
        val old =
            AtJson.decodeFromString(
                SegmentPrefsRecord.serializer(),
                """{"${'$'}type":"${Nsid.ACTOR_SEGMENT_PREFS}","matching":true,"updatedAt":"2026-10-15T12:00:00Z"}""",
            )
        assertNull(old.hideFromLeaderboards)
        assertFalse("hideFromLeaderboards" in AtJson.encodeToJsonElement(SegmentPrefsRecord.serializer(), old).jsonObject)

        val segment = AtUri.of(alice, Nsid.FEED_SEGMENT, "3l2abcdefgh44")
        val effort =
            EffortView(
                SegmentViewBasic(segment, "Hill climb", SegmentSportGroup.RIDE, 1_200),
                activity,
                startOffset = 120,
                elapsed = 611,
                startedOn = "2026-10-15",
                best = true,
                rank = 3,
            )
        val entry = LeaderboardEntry(rank = 3, athlete = ProfileViewBasic(alice, Handle("alice.test")), effort = effort)
        val output = GetSegmentLeaderboard.Output(entries = listOf(entry), viewer = entry, cursor = "611::did:plc:alice")
        val outputJson = AtJson.encodeToJsonElement(GetSegmentLeaderboard.Output.serializer(), output).jsonObject
        assertEquals(output, AtJson.decodeFromJsonElement(GetSegmentLeaderboard.Output.serializer(), outputJson))
        assertFalse("notRanked" in outputJson["entries"]!!.jsonArray[0].jsonObject["effort"]!!.jsonObject)
        val unranked = effort.copy(best = null, rank = null, notRanked = NotRanked.IMPLAUSIBLE)
        assertEquals(
            unranked,
            AtJson.decodeFromJsonElement(EffortView.serializer(), AtJson.encodeToJsonElement(EffortView.serializer(), unranked)),
        )
        val bare =
            """{"segment":{"uri":"$segment","name":"Hill","sport":"ride","distanceMeters":1200},"activity":"$activity",""" +
                """"startOffset":1,"elapsed":2,"startedOn":"2026-10-15"}"""
        val old2 = AtJson.decodeFromString(EffortView.serializer(), bare)
        assertNull(old2.rank)
        assertNull(old2.notRanked)
    }

    @Test
    fun leaderboardParamsRoundTrip() {
        val segment = AtUri.of(Did("did:plc:alice"), Nsid.FEED_SEGMENT, "3l2abcdefgh44")
        val params =
            GetSegmentLeaderboard.Params(
                segment,
                LeaderboardPeriod.MONTH,
                LeaderboardScope.FOLLOWING,
                eBike = true,
                ageGroup = LeaderboardAgeGroup.FROM_75,
                gender = LeaderboardGender.NON_BINARY,
                limit = 30,
            )
        val encoded = XrpcParams.encode(GetSegmentLeaderboard.Params.serializer(), params)
        assertTrue("eBike" to "true" in encoded)
        assertTrue("ageGroup" to "75+" in encoded)
        assertEquals(
            params,
            XrpcParams.decode(GetSegmentLeaderboard.Params.serializer(), encoded.groupBy({ it.first }, { it.second })),
        )
    }

    @Test
    fun mayorsRoundTrip() {
        val segment = AtUri.of(Did("did:plc:alice"), Nsid.FEED_SEGMENT, "3l2abcdefgh44")
        val params = GetSegmentMayors.Params(segment, MayorPeriod.LAST_WEEK, LeaderboardScope.FOLLOWING, eBike = true, limit = 1)
        val encoded = XrpcParams.encode(GetSegmentMayors.Params.serializer(), params)
        assertTrue("period" to "lastWeek" in encoded)
        assertEquals(params, XrpcParams.decode(GetSegmentMayors.Params.serializer(), encoded.groupBy({ it.first }, { it.second })))

        val alice = Did("did:plc:alice")
        val entry = MayorEntry(rank = 1, athlete = ProfileViewBasic(alice, Handle("alice.test")), efforts = 14, reachedOn = "2026-10-18")
        val output = GetSegmentMayors.Output(entries = listOf(entry), viewer = entry, cursor = "14::2026-10-18::did:plc:alice")
        val json = AtJson.encodeToJsonElement(GetSegmentMayors.Output.serializer(), output).jsonObject
        assertEquals("2026-10-18", json["entries"]!!.jsonArray[0].jsonObject["reachedOn"]?.jsonPrimitive?.content)
        assertEquals(output, AtJson.decodeFromJsonElement(GetSegmentMayors.Output.serializer(), json))
    }
}
