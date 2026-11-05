package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.CheckDiveSites
import io.github.solcott.activitytracker.lexicon.app.DiveSiteConditions
import io.github.solcott.activitytracker.lexicon.app.DiveSiteMonth
import io.github.solcott.activitytracker.lexicon.app.DiveSitePosition
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRecord
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRegularEntry
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.lexicon.app.DiveSiteViewBasic
import io.github.solcott.activitytracker.lexicon.app.DiveSiteViewerState
import io.github.solcott.activitytracker.lexicon.app.GetDiveSiteConditions
import io.github.solcott.activitytracker.lexicon.app.GetDiveSiteDives
import io.github.solcott.activitytracker.lexicon.app.GetDiveSiteRegulars
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.RegularsPeriod
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidateCheck
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `feed.diveSite`, its views and queries as `AtJson` writes and reads them. */
class DiveSiteSerializationTest {
    private val alice = Did("did:plc:alice")
    private val siteUri = AtUri.of(alice, Nsid.FEED_DIVE_SITE, "3l2abcdefgh22")

    private fun record(derivedFrom: StrongRef? = null) =
        DiveSiteRecord(
            name = "Blue Hole",
            lat = 284_000_000,
            lng = 345_000_000,
            radius = DiveSiteRecord.DEFAULT_RADIUS,
            source = SegmentSource.ATHLETE,
            license = "CC0-1.0",
            createdAt = Instant.parse("2026-10-15T12:00:00Z"),
            derivedFrom = derivedFrom,
        )

    @Test
    fun recordRoundTripsWithItsType() {
        val withSource = record(StrongRef(AtUri.of(alice, Nsid.FEED_ACTIVITY, "3l2abcdefgh33"), Cid("bafyactivity")))
        val json = AtJson.encodeToJsonElement(DiveSiteRecord.serializer(), withSource).jsonObject
        assertEquals(Nsid.FEED_DIVE_SITE, json["\$type"]?.jsonPrimitive?.content)
        assertEquals(withSource, AtJson.decodeFromJsonElement(DiveSiteRecord.serializer(), json))
    }

    @Test
    fun recordOmitsNullsAndIgnoresUnknownFields() {
        val json = AtJson.encodeToJsonElement(DiveSiteRecord.serializer(), record()).jsonObject
        assertFalse("derivedFrom" in json)
        assertFalse("attribution" in json)
        val decoded =
            AtJson.decodeFromString(
                DiveSiteRecord.serializer(),
                """{"${'$'}type":"${Nsid.FEED_DIVE_SITE}","name":"Reef","lat":1,"lng":2,"radius":150,"source":"osm",""" +
                    """"license":"ODbL-1.0","createdAt":"2026-10-15T12:00:00Z","later":true}""",
            )
        assertEquals(150, decoded.radius)
        assertNull(decoded.derivedFrom)
    }

    @Test
    fun viewFallsBackToTheOriginalName() {
        val view =
            DiveSiteView(
                uri = siteUri,
                cid = Cid("bafysite"),
                creator = ProfileViewBasic(did = alice, handle = Handle("alice.test")),
                record = record(),
                status = SegmentStatus.PROPOSED,
                viewer = DiveSiteViewerState(canVoteOnNames = true),
            )
        val json = AtJson.encodeToJsonElement(DiveSiteView.serializer(), view)
        val decoded = AtJson.decodeFromJsonElement(DiveSiteView.serializer(), json)
        assertEquals(view, decoded)
        assertEquals("Blue Hole", decoded.currentName)
        assertEquals("Shark Alley", decoded.copy(name = "Shark Alley").currentName)
    }

    @Test
    fun queryOutputsRoundTrip() {
        val conditions =
            GetDiveSiteConditions.Output(
                DiveSiteConditions(
                    dives = 8,
                    minTemperature = 180,
                    medianTemperature = 220,
                    maxTemperature = 260,
                    months = listOf(DiveSiteMonth(month = 2, dives = 4, medianTemperature = 180)),
                )
            )
        val conditionsJson = AtJson.encodeToString(GetDiveSiteConditions.Output.serializer(), conditions)
        assertEquals(conditions, AtJson.decodeFromString(GetDiveSiteConditions.Output.serializer(), conditionsJson))
        assertNull(AtJson.decodeFromString(GetDiveSiteConditions.Output.serializer(), "{}").conditions)

        val entry =
            DiveSiteRegularEntry(rank = 1, athlete = ProfileViewBasic(alice, Handle("alice.test")), visits = 6, reachedOn = "2026-10-01")
        val regulars = GetDiveSiteRegulars.Output(entries = listOf(entry), viewer = entry)
        val regularsJson = AtJson.encodeToString(GetDiveSiteRegulars.Output.serializer(), regulars)
        assertEquals(regulars, AtJson.decodeFromString(GetDiveSiteRegulars.Output.serializer(), regularsJson))

        val check = CheckDiveSites.Output(listOf(SegmentCandidateCheck("osm-node-1", listOf(siteUri))))
        assertEquals(
            check,
            AtJson.decodeFromString(CheckDiveSites.Output.serializer(), AtJson.encodeToString(CheckDiveSites.Output.serializer(), check)),
        )
        val input = CheckDiveSites.Input(listOf(DiveSitePosition("osm-node-1", 1, 2)))
        assertEquals(
            input,
            AtJson.decodeFromString(CheckDiveSites.Input.serializer(), AtJson.encodeToString(CheckDiveSites.Input.serializer(), input)),
        )
    }

    @Test
    fun basicViewAndPagingBounds() {
        val basic = DiveSiteViewBasic(siteUri, "Blue Hole", SegmentStatus.ACTIVE)
        assertEquals(
            basic,
            AtJson.decodeFromString(DiveSiteViewBasic.serializer(), AtJson.encodeToString(DiveSiteViewBasic.serializer(), basic)),
        )
        assertEquals(GetDiveSiteDives.MAX_LIMIT, GetDiveSiteDives.clamp(500))
        assertEquals(1, GetDiveSiteDives.clamp(0))
        assertEquals(listOf("allTime", "thisYear", "lastYear"), RegularsPeriod.all)
    }
}
