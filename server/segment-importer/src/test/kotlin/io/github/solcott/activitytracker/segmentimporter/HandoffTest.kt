package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRecord
import io.github.solcott.activitytracker.lexicon.app.GetHandoffs
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SubjectHandoff
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `handoff` (segments/segment-handoffs, dive-sites/site-handoffs): pending handoffs republished under their ids, never twice. */
class HandoffTest {
    private val fake = FakeServices()
    private val clients = Clients(fake.http, fake.settings)
    private val clock =
        object : Clock {
            override fun now() = Instant.parse("2026-11-06T12:00:00Z")
        }

    /** Records in the fake repo, by collection and rkey. */
    private val repo = mutableMapOf<Pair<String, String>, String>()

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
        fake.on("com.atproto.repo.getRecord") { call ->
            val key = call.params.getValue("collection") to call.params.getValue("rkey")
            repo[key]?.let { json("""{"uri":"at://${FakeServices.DID}/${key.first}/${key.second}","value":$it}""") }
                ?: error(HttpStatusCode.BadRequest, "RecordNotFound")
        }
        fake.on("com.atproto.repo.createRecord") { call ->
            val key = call.body!!.getValue("collection").jsonPrimitive.content to call.body.getValue("rkey").jsonPrimitive.content
            if (key in repo) return@on error(HttpStatusCode.BadRequest, "InvalidRequest", "Record already exists")
            repo[key] = call.body.getValue("record").toString()
            json("""{"uri":"at://${FakeServices.DID}/${key.first}/${key.second}","cid":"bafy${key.second}"}""")
        }
    }

    @Test
    fun aSegmentHandoffIsRepublishedAsAnAthleteSegmentWithNoAttribution() = runTest {
        services(segment)
        val result = handoff(clients, clock)
        assertEquals(listOf("handoff-aa"), result.published)
        assertNull(result.stoppedBy)
        val record =
            AtJson.decodeFromJsonElement(
                SegmentRecord.serializer(),
                fake.calls("com.atproto.repo.createRecord").single().body!!.getValue("record").jsonObject,
            )
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
            AtJson.decodeFromJsonElement(
                DiveSiteRecord.serializer(),
                repo.getValue(Nsid.FEED_DIVE_SITE to "handoff-bb").let { AtJson.parseToJsonElement(it).jsonObject },
            ),
        )
    }

    @Test
    fun aSecondRunWritesNothing() = runTest {
        services(segment, site)
        handoff(clients, clock)
        val again = handoff(clients, clock)
        assertEquals(emptyList(), again.published)
        assertEquals(listOf("handoff-aa", "handoff-bb"), again.present)
        assertEquals(2, fake.calls("com.atproto.repo.createRecord").size)
    }

    @Test
    fun anUnknownKindIsSkipped() = runTest {
        services(SubjectHandoff("handoff-cc", "route", "Mystery"))
        assertEquals(listOf("handoff-cc"), handoff(clients, clock).skipped)
        assertEquals(emptyList(), fake.calls("com.atproto.repo.createRecord"))
    }

    @Test
    fun theCommandTakesNoArguments() {
        assertEquals(Command.Handoff, parse(listOf("handoff")))
        assertNull(parse(listOf("handoff", "extra")))
    }
}
