package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.CheckDiveSites
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidateCheck
import io.ktor.http.HttpStatusCode
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `publish-sites` (dive-sites/site-import): extracted sites that duplicate nothing become dive sites, never doubled or rewritten. */
class PublishSitesTest {
    private val fake = FakeServices()
    private val clients = Clients(fake.http, fake.settings)
    private val clock =
        object : Clock {
            override fun now() = Instant.parse("2026-10-21T12:00:00Z")
        }

    /** Records in the fake repo, by rkey. */
    private val repo = mutableMapOf<String, String>()

    /** Sites [appView]'s check reports as within 50 m of an athlete's live site. */
    private val nearAthleteSite = mutableSetOf<String>()

    /** Sites whose own published record [appView]'s check reports, as it does once the AppView has indexed it. */
    private val indexed = mutableSetOf<String>()

    private val athleteSite = AtUri("at://did:plc:alice/${Nsid.FEED_DIVE_SITE}/3kx")

    private fun site(id: String, name: String = "Site $id") = SiteEntry(id, name, 274_000_000, 340_000_000, 150, "osm", "ODbL-1.0")

    private fun appView() {
        fake.on(Nsid.IMPORTER_CHECK_DIVE_SITES) { call ->
            val input = AtJson.decodeFromJsonElement(CheckDiveSites.Input.serializer(), call.body!!)
            val checked =
                input.sites.map { site ->
                    val own = listOfNotNull(AtUri.of(Did(FakeServices.DID), Nsid.FEED_DIVE_SITE, site.id).takeIf { site.id in indexed })
                    SegmentCandidateCheck(site.id, own + listOfNotNull(athleteSite.takeIf { site.id in nearAthleteSite }))
                }
            json(AtJson.encodeToString(CheckDiveSites.Output.serializer(), CheckDiveSites.Output(checked)))
        }
        fake.on("com.atproto.repo.getRecord") { call ->
            val rkey = call.params.getValue("rkey")
            repo[rkey]?.let { json("""{"uri":"at://${FakeServices.DID}/${Nsid.FEED_DIVE_SITE}/$rkey","value":$it}""") }
                ?: error(HttpStatusCode.BadRequest, "RecordNotFound")
        }
        fake.on("com.atproto.repo.createRecord") { call ->
            val rkey = call.body!!.getValue("rkey").jsonPrimitive.content
            if (rkey in repo) return@on error(HttpStatusCode.BadRequest, "InvalidRequest", "Record already exists")
            repo[rkey] = call.body.getValue("record").toString()
            json("""{"uri":"at://${FakeServices.DID}/${Nsid.FEED_DIVE_SITE}/$rkey","cid":"bafy$rkey"}""")
        }
    }

    @Test
    fun aSiteThatDuplicatesNothingIsPublishedUnderItsId() = runTest {
        appView()
        val result = publishSites(clients, listOf(site("osm-node-1", "SS Thistlegorm")), clock)
        assertEquals(listOf("osm-node-1"), result.published)
        assertNull(result.stoppedBy)
        val create = fake.calls("com.atproto.repo.createRecord").single().body!!
        assertEquals(Nsid.FEED_DIVE_SITE, create.getValue("collection").jsonPrimitive.content)
        assertEquals("osm-node-1", create.getValue("rkey").jsonPrimitive.content)
        assertEquals(
            DiveSiteRecord("SS Thistlegorm", 274_000_000, 340_000_000, 150, "osm", "ODbL-1.0", Instant.parse("2026-10-21T12:00:00Z")),
            AtJson.decodeFromJsonElement(DiveSiteRecord.serializer(), create.getValue("record").jsonObject),
        )
        assertEquals(
            "Bearer svc:${FakeServices.APPVIEW_DID}:${Nsid.IMPORTER_CHECK_DIVE_SITES}",
            fake.calls(Nsid.IMPORTER_CHECK_DIVE_SITES).single().auth,
        )
    }

    @Test
    fun aSiteNearALiveOneIsADuplicateAndIsNotPublished() = runTest {
        appView()
        nearAthleteSite += "osm-node-1"
        val result = publishSites(clients, listOf(site("osm-node-1"), site("osm-node-2")), clock)
        assertEquals(listOf("osm-node-1"), result.duplicates)
        assertEquals(listOf("osm-node-2"), result.published)
        assertEquals(setOf("osm-node-2"), repo.keys)
    }

    @Test
    fun aSecondRunWritesNothing() = runTest {
        appView()
        val sites = listOf(site("osm-node-1"), site("osm-way-2"))
        publishSites(clients, sites, clock)
        val written = repo.toMap()
        indexed += "osm-node-1" // The AppView has indexed one of them; the other it hasn't yet.
        val again = publishSites(clients, sites, clock)
        assertEquals(emptyList(), again.published)
        assertEquals(listOf("osm-node-1", "osm-way-2"), again.present)
        assertEquals(2, fake.calls("com.atproto.repo.createRecord").size)
        assertEquals(written, repo)
    }

    @Test
    fun aPublishedSiteIsNeverRewrittenEvenWhenTheFileChanged() = runTest {
        appView()
        repo["osm-node-1"] = """{"name":"Old name"}"""
        val result = publishSites(clients, listOf(site("osm-node-1", "New name")), clock)
        assertEquals(listOf("osm-node-1"), result.present)
        assertEquals("""{"name":"Old name"}""", repo["osm-node-1"])
        assertEquals(emptyList(), fake.calls("com.atproto.repo.createRecord"))
        assertEquals(emptyList(), fake.calls("com.atproto.repo.putRecord") + fake.calls("com.atproto.repo.deleteRecord"))
    }

    @Test
    fun sitesAreCheckedInPagesOf500() = runTest {
        appView()
        val result = publishSites(clients, (1..1001).map { site("osm-node-$it") }, clock)
        assertEquals(1001, result.published.size)
        val pages = fake.calls(Nsid.IMPORTER_CHECK_DIVE_SITES).map { it.body!!.getValue("sites").jsonArray.size }
        assertEquals(listOf(500, 500, 1), pages)
    }

    @Test
    fun aFailedCheckStopsTheRunKeepingWhatItDid() = runTest {
        appView()
        var checks = 0
        fake.on(Nsid.IMPORTER_CHECK_DIVE_SITES) { call ->
            if (++checks == 2) return@on error(HttpStatusCode.InternalServerError, "InternalServerError")
            val input = AtJson.decodeFromJsonElement(CheckDiveSites.Input.serializer(), call.body!!)
            val checked = input.sites.map { SegmentCandidateCheck(it.id, emptyList()) }
            json(AtJson.encodeToString(CheckDiveSites.Output.serializer(), CheckDiveSites.Output(checked)))
        }
        val result = publishSites(clients, (1..501).map { site("osm-node-$it") }, clock)
        assertEquals(500, result.published.size)
        assertIs<Exception>(result.stoppedBy)
        assertEquals(500, repo.size)
    }

    @Test
    fun theSitesFileReadsBack() {
        val out = StringWriter()
        writeSites(out, "eg-red-sea", null, emptyList())
        val line = AtJson.encodeToString(SiteEntry.serializer(), site("osm-node-1"))
        assertEquals(SitesFile("eg-red-sea", listOf(site("osm-node-1"))), readSites((out.toString() + line).lineSequence()))
        assertFailsWith<IllegalArgumentException> { readSites(emptySequence()) }
        assertFailsWith<IllegalArgumentException> {
            readSites(sequenceOf("""{"region":"eg-red-sea","sitesVersion":${SITES_VERSION + 1}}"""))
        }
    }

    @Test
    fun publishSitesTakesOneFile() {
        assertEquals(Command.PublishSites(java.io.File("sites.ndjson")), parse(listOf("publish-sites", "sites.ndjson")))
        assertNull(parse(listOf("publish-sites")))
        assertNull(parse(listOf("publish-sites", "--region", "x")))
        assertNull(parse(listOf("publish-sites", "a.ndjson", "b.ndjson")))
    }
}
