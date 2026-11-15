package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRecord
import io.github.solcott.activitytracker.lexicon.app.GetHandoffs
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SubjectHandoff
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

/** `handoff` (segments/segment-handoffs, dive-sites/site-handoffs): pending handoffs republished under their ids, never twice. */
class HandoffTest {
    private val fake = FakeServices()
    private val clients = Clients(fake.http, fake.settings)
    private val clock =
        object : Clock {
            override fun now() = Instant.parse("2026-11-06T12:00:00Z")
        }

    /** Records in the fake repo, by collection and rkey. */
    private val repo = mutableMapOf<Pair<String, String>, JsonObject>()

    private val segment =
        SubjectHandoff(
            "handoff-aa",
            SubjectHandoff.SEGMENT,
            "Hogback Climb",
            sport = "ride",
            segmentKind = "run",
            line = "_p~iF~ps|U_ulLnnqC",
        )
    private val site = SubjectHandoff("handoff-bb", SubjectHandoff.SITE, "Blue Hole", lat = 274_000_000, lng = 340_000_000, radius = 100)

    private fun services(vararg pending: SubjectHandoff) {
        fake.on(Nsid.IMPORTER_GET_HANDOFFS) {
            json(AtJson.encodeToString(GetHandoffs.Output.serializer(), GetHandoffs.Output(pending.toList())))
        }
        fake.serveRepo(repo)
    }

    @Test
    fun aSegmentHandoffIsRepublishedAsAnAthleteSegmentWithNoAttribution() = runTest {
        services(segment)
        val result = handoff(clients, clock)
        assertEquals(listOf("handoff-aa"), result.published)
        assertNull(result.stoppedBy)
        val record = AtJson.decodeFromJsonElement(SegmentRecord.serializer(), repo.getValue(Nsid.FEED_SEGMENT to "handoff-aa"))
        assertEquals(
            SegmentRecord(
                name = "Hogback Climb",
                sport = "ride",
                line = segment.line!!,
                source = "athlete",
                license = "CC0-1.0",
                createdAt = Instant.parse("2026-11-06T12:00:00Z"),
                kind = "run",
            ),
            record,
        )
        assertEquals(
            "Bearer svc:${FakeServices.APPVIEW_DID}:${Nsid.IMPORTER_GET_HANDOFFS}",
            fake.calls(Nsid.IMPORTER_GET_HANDOFFS).single().auth,
        )
    }

    @Test
    fun aSiteHandoffIsRepublishedAsADiveSite() = runTest {
        services(site)
        handoff(clients, clock)
        assertEquals(
            DiveSiteRecord("Blue Hole", 274_000_000, 340_000_000, 100, "athlete", "CC0-1.0", Instant.parse("2026-11-06T12:00:00Z")),
            AtJson.decodeFromJsonElement(DiveSiteRecord.serializer(), repo.getValue(Nsid.FEED_DIVE_SITE to "handoff-bb")),
        )
    }

    @Test
    fun aSecondRunWritesNothing() = runTest {
        services(segment, site)
        handoff(clients, clock)
        val written = repo.toMap()
        val again = handoff(clients, clock)
        assertEquals(emptyList(), again.published)
        assertEquals(listOf("handoff-aa", "handoff-bb"), again.present)
        assertEquals(written, repo)
        assertEquals(listOf(2, 2), fake.commits().map { it.size }, "the first run's commit, then the second's, refused")
    }

    @Test
    fun anUnknownKindIsSkipped() = runTest {
        services(SubjectHandoff("handoff-cc", "route", "Mystery"))
        assertEquals(listOf("handoff-cc"), handoff(clients, clock).skipped)
        assertEquals(emptyList(), fake.calls(FakeServices.APPLY_WRITES))
    }

    @Test
    fun theCommandTakesNoArguments() {
        assertEquals(Command.Handoff, parse(listOf("handoff")))
        assertNull(parse(listOf("handoff", "extra")))
    }
}
