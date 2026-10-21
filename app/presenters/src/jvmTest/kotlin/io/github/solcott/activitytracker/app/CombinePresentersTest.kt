package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import app.cash.turbine.ReceiveTurbine
import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailPresenter
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.app.detail.CombinePickerEvent
import io.github.solcott.activitytracker.app.detail.CombinePickerPresenter
import io.github.solcott.activitytracker.app.detail.CombinePickerScreen
import io.github.solcott.activitytracker.app.detail.CombinePickerState
import io.github.solcott.activitytracker.app.detail.DetailSection
import io.github.solcott.activitytracker.app.detail.StreamKind
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.data.activity.ActivityExporter
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityPlaces
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.CombineCue
import io.github.solcott.activitytracker.data.activity.CombineSuggestions
import io.github.solcott.activitytracker.data.activity.DataKind
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.activity.RouteTrimmer
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamsRecord
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Combining from an activity's page (`feed/combined-activities`): the picker, and the combined activity's own actions. */
class CombinePresentersTest {
    private val alice = Did("did:plc:alice")
    private val aliceProfile = """{"did":"did:plc:alice","handle":"alice.test"}"""

    private fun uri(rkey: String) = "at://${alice.value}/${Nsid.FEED_ACTIVITY}/$rkey"

    /** Alice's repo: `rkey → feed.activity`, as the PDS holds it and the AppView serves it. */
    private val records = mutableMapOf<String, JsonObject>()

    /** Every write the PDS took, in order, as "put|delete collection/rkey". */
    private val writes = mutableListOf<String>()

    /** Holds each `putRecord` until completed. */
    private var gate = CompletableDeferred(Unit)
    private var failReads = false
    private var failByStart = false
    private var byStartQueries = 0

    /** Suggestions look at the last week: a day after the activities here. */
    private val suggestionClock =
        object : Clock {
            override fun now() = Instant.parse("2026-10-15T00:00:00Z")
        }

    /** Alice's `feed.activityStreams` records, by rkey, and the blobs they name, by CID. */
    private val streamsRecords = mutableMapOf<String, JsonObject>()
    private val blobs = mutableMapOf<String, ByteArray>()

    /**
     * The streams the AppView serves, by rkey: set by the test alone, so they lag what the PDS holds, as the real one does until Jetstream
     * brings it a rebuild.
     */
    private val servedStreams = mutableMapOf<String, ActivityStreamData>()

    /** Activities the AppView serves as Bob's, not Alice's own. */
    private val bobs = mutableSetOf<String>()

    /** Stores [data] as [rkey]'s streams, in the repo. */
    private fun streams(rkey: String, data: ActivityStreamData) {
        val bytes = AtJson.encodeToString(ActivityStreamData.serializer(), data).encodeToByteArray()
        val cid = "bafyblob${blobs.size}"
        blobs[cid] = bytes
        val record =
            ActivityStreamsRecord(
                activity = AtUri(uri(rkey)),
                channels = data.channels,
                createdAt = Clock.System.now(),
                data = BlobRef(ref = BlobRef.Link(cid), mimeType = ActivityStreamsRecord.MIME_TYPE, size = bytes.size.toLong()),
            )
        streamsRecords[rkey] = AtJson.encodeToJsonElement(ActivityStreamsRecord.serializer(), record).jsonObject
    }

    private fun streamsOf(rkey: String): ActivityStreamData? {
        val cid = streamsRecords[rkey]?.let { AtJson.decodeFromJsonElement(ActivityStreamsRecord.serializer(), it) }?.data?.ref?.link
        return cid?.let { AtJson.decodeFromString(ActivityStreamData.serializer(), blobs.getValue(it).decodeToString()) }
    }

    private fun ride(start: String, minutes: Int = 60, members: List<String> = emptyList()): JsonObject {
        val combined =
            members
                .takeIf { it.isNotEmpty() }
                ?.joinToString(",", prefix = ""","combined":{"members":[""", postfix = """],"sources":[]}""") { "\"${uri(it)}\"" }
                .orEmpty()
        return AtJson.parseToJsonElement(
                """{"sport":"ride","startedAt":"$start","elapsedTime":${minutes * 60},"createdAt":"$start"$combined}"""
            )
            .jsonObject
    }

    /** The AppView's view of [rkey]: its members, and the combined activity it's part of. */
    private fun view(rkey: String): String {
        val record = records.getValue(rkey)
        val combinedInto =
            records.entries.firstOrNull { (_, r) -> uri(rkey) in r.membersOf() }?.let { (key, _) -> ""","combinedInto":"${uri(key)}"""" }
        val members =
            record
                .membersOf()
                .takeIf { it.isNotEmpty() }
                ?.joinToString(",", prefix = ""","members":[""", postfix = "]") { m ->
                    val r = AtJson.decodeFromJsonElement(ActivityRecord.serializer(), records.getValue(m.substringAfterLast('/')))
                    """{"uri":"$m","startedAt":"${r.startedAt}","elapsedTime":${r.elapsedTime}}"""
                }
        val author = if (rkey in bobs) """{"did":"did:plc:bob","handle":"bob.test"}""" else aliceProfile
        return """{"uri":"${uri(rkey)}","cid":"bafy$rkey","author":$author,"record":$record,""" +
            """"likeCount":0,"commentCount":0,"indexedAt":"2026-10-14T12:00:00Z","viewer":{}${combinedInto.orEmpty()}${members.orEmpty()}}"""
    }

    private fun JsonObject.membersOf(): List<String> =
        AtJson.decodeFromJsonElement(ActivityRecord.serializer(), this).combined?.members.orEmpty().map { it.value }

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                fun notFound() = respond("""{"error":"NotFound"}""", HttpStatusCode.BadRequest, json)
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY}" -> {
                        val rkey = request.url.parameters["uri"]!!.substringAfterLast('/')
                        if (rkey in records) respond("""{"activity":${view(rkey)},"comments":[]}""", headers = json) else notFound()
                    }

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY_STREAMS}" -> {
                        val uri = AtUri(request.url.parameters["uri"]!!)
                        val data = servedStreams[uri.rkey] ?: return@MockEngine notFound()
                        val output = GetActivityStreams.Output(uri, Visibility.PUBLIC, data.channels, data)
                        respond(AtJson.encodeToString(GetActivityStreams.Output.serializer(), output), headers = json)
                    }

                    "/xrpc/${Nsid.FEED_GET_AUTHOR_ACTIVITIES_BY_START}" -> {
                        byStartQueries++
                        if (failByStart) {
                            respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        } else {
                            respond("""{"activities":[${records.keys.sorted().joinToString(",") { view(it) }}]}""", headers = json)
                        }
                    }

                    "/xrpc/com.atproto.repo.getRecord" -> {
                        if (failReads)
                            return@MockEngine respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        val rkey = request.url.parameters["rkey"]!!
                        val record =
                            when (request.url.parameters["collection"]) {
                                Nsid.FEED_ACTIVITY -> records[rkey]
                                Nsid.FEED_ACTIVITY_STREAMS -> streamsRecords[rkey]
                                else -> null
                            }
                        if (record == null) respond("""{"error":"RecordNotFound"}""", HttpStatusCode.BadRequest, json)
                        else respond("""{"uri":"${uri(rkey)}","cid":"bafy$rkey","value":$record}""", headers = json)
                    }

                    "/xrpc/com.atproto.repo.putRecord" -> {
                        gate.await()
                        val body =
                            AtJson.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                                .jsonObject
                        val collection = body.getValue("collection").jsonPrimitive.content
                        val rkey = body.getValue("rkey").jsonPrimitive.content
                        writes += "put $collection/$rkey"
                        if (collection == Nsid.FEED_ACTIVITY) records[rkey] = body.getValue("record").jsonObject
                        if (collection == Nsid.FEED_ACTIVITY_STREAMS) streamsRecords[rkey] = body.getValue("record").jsonObject
                        respond("""{"uri":"${uri(rkey)}","cid":"bafyrec"}""", headers = json)
                    }

                    "/xrpc/com.atproto.repo.uploadBlob" -> {
                        val bytes = (request.body as OutgoingContent.ByteArrayContent).bytes()
                        val cid = "bafyblob${blobs.size}"
                        blobs[cid] = bytes
                        respond(
                            """{"blob":{"${'$'}type":"blob","ref":{"${'$'}link":"$cid"},""" +
                                """"mimeType":"application/json","size":${bytes.size}}}""",
                            headers = json,
                        )
                    }

                    "/xrpc/com.atproto.sync.getBlob" -> {
                        if (failReads)
                            return@MockEngine respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        blobs[request.url.parameters["cid"]]?.let { respond(it, headers = json) } ?: notFound()
                    }

                    "/xrpc/com.atproto.repo.deleteRecord" -> {
                        val body =
                            AtJson.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                                .jsonObject
                        val collection = body.getValue("collection").jsonPrimitive.content
                        val rkey = body.getValue("rkey").jsonPrimitive.content
                        writes += "delete $collection/$rkey"
                        if (collection == Nsid.FEED_ACTIVITY) records -= rkey
                        if (collection == Nsid.FEED_ACTIVITY_STREAMS) streamsRecords -= rkey
                        respond("{}", headers = json)
                    }

                    else -> notFound()
                }
            }
        ) {
            expectSuccess = false
        }

    private val localLikes = LocalLikes()

    @Suppress("LongParameterList") // One per repository the presenters under test need.
    private class Graph(
        val feed: FeedRepository,
        val activities: ActivityRepository,
        val profiles: ProfileRepository,
        val manager: ActivityManager,
        val exporter: ActivityExporter,
        val suggestions: CombineSuggestions,
        val blocks: BlockRepository,
        val reports: ReportRepository,
    )

    /** A session that can write to the repo only, so the PDS here is just Alice's repo. */
    private suspend fun graph(): Graph {
        val (db, _, clients) = signedInTestSession(http, did = alice, scope = "atproto transition:generic")
        val feed = FeedRepository(db, clients, localLikes, Logger)
        val zones = TestZones(db, clients)
        val (outbox, writer, streams) = Triple(zones.outbox, zones.writer, zones.streams)
        val tracks = zones.tracks
        val privacy = PrivacyZones(clients, Clock.System, Logger)
        val places = ActivityPlaces(clients, Clock.System, Logger)
        val activities =
            ActivityRepository(
                db,
                clients,
                outbox,
                writer,
                streams,
                tracks,
                zones.sources,
                zones.devices,
                zones.store,
                places,
                privacy,
                RouteTrimmer(db, clients, writer, outbox, privacy, tracks, feed, Clock.System, Logger),
                feed,
                localLikes,
                RecentActivityTypes(db, clients, feed),
                Clock.System,
                testSegments(db, clients, writer, outbox, privacy, tracks).tracks,
                testSegments(db, clients, writer, outbox, privacy, tracks).sweeper,
            )
        val manager =
            ActivityManager(
                db,
                clients,
                outbox,
                writer,
                streams,
                tracks,
                zones.sources,
                zones.devices,
                zones.store,
                places,
                feed,
                privacy,
                Clock.System,
                activities,
                testSegments(db, clients, writer, outbox, privacy, tracks).tracks,
            )
        return Graph(
            feed,
            activities,
            ProfileRepository(clients, Clock.System, Logger, zones.store),
            manager,
            ActivityExporter(db, clients, tracks, zones.sources, feed),
            CombineSuggestions(db, clients, feed, tracks, suggestionClock),
            BlockRepository(clients, writer, outbox, feed, Clock.System),
            ReportRepository(clients),
        )
    }

    private suspend fun picker(uri: String, navigator: FakeNavigator): @Composable () -> CombinePickerState {
        val g = graph()
        return { CombinePickerPresenter(CombinePickerScreen(uri), navigator, g.feed, g.manager) }
    }

    private suspend fun detail(
        uri: String,
        navigator: FakeNavigator,
        suggestOnOpen: Boolean = false,
        shared: Graph? = null,
    ): @Composable () -> ActivityDetailState {
        val g = shared ?: graph()
        val units = UnitsRepository(g.profiles, Clock.System, Logger, UnitSystem.Metric)
        return {
            ActivityDetailPresenter(
                ActivityDetailScreen(uri, suggestOnOpen),
                navigator,
                g.feed,
                g.activities,
                g.profiles,
                localLikes,
                g.manager,
                g.exporter,
                units,
                g.suggestions,
                g.blocks,
                g.reports,
            )
        }
    }

    private fun day() {
        records["3kwatch"] = ride("2026-10-14T09:00:00Z")
        records["3kedge"] = ride("2026-10-14T08:58:00Z", minutes = 63)
        records["3kevening"] = ride("2026-10-14T18:00:00Z")
        records["3kother"] = ride("2026-10-14T06:00:00Z")
        records["3kday"] = ride("2026-10-14T06:00:00Z", members = listOf("3kother", "3kmorning"))
        records["3kmorning"] = ride("2026-10-14T06:30:00Z")
    }

    @Test
    fun thePickerOffersOnlyWhatCanBeCombined() = runTest {
        day()
        presenterTestOf(picker(uri("3kwatch"), FakeNavigator(CombinePickerScreen(uri("3kwatch"))))) {
            val state = awaitUntil { it.candidates != null }
            // Not itself, not a combined activity, not another one's member; oldest first.
            assertEquals(listOf("3kedge", "3kevening"), state.candidates?.map { it.uri.rkey })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedLoadShowsAnErrorAndRetries() = runTest {
        day()
        failByStart = true
        presenterTestOf(picker(uri("3kwatch"), FakeNavigator(CombinePickerScreen(uri("3kwatch"))))) {
            val failed = awaitUntil { it.error != null }
            assertEquals(UiError.Kind.LoadCombineCandidates, failed.error?.kind)
            failByStart = false
            failed.eventSink(CombinePickerEvent.Retry)
            assertEquals(2, awaitUntil { it.candidates != null && it.error == null }.candidates?.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun pickingCombinesOnceAndOpensTheCombinedActivity() = runTest {
        day()
        gate = CompletableDeferred()
        val navigator = FakeNavigator(ActivityDetailScreen(uri("3kwatch")), CombinePickerScreen(uri("3kwatch")))
        presenterTestOf(picker(uri("3kwatch"), navigator)) {
            val loaded = awaitUntil { it.candidates != null }
            loaded.eventSink(CombinePickerEvent.Pick(AtUri(uri("3kedge"))))
            awaitUntil { it.combining != null }
            loaded.eventSink(CombinePickerEvent.Pick(AtUri(uri("3kedge"))))
            gate.complete(Unit)

            navigator.awaitPop()
            navigator.awaitPop()
            val opened = assertIs<ActivityDetailScreen>(navigator.awaitNextScreen())
            val combined = opened.uri.substringAfterLast('/')
            assertEquals(setOf(uri("3kwatch"), uri("3kedge")), records.getValue(combined).membersOf().toSet())
            assertEquals(1, writes.count { it == "put ${Nsid.FEED_ACTIVITY}/$combined" }, "a second tap does nothing")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedCombineShowsAnError() = runTest {
        day()
        // The members can't be read from the PDS to build it from.
        failReads = true
        presenterTestOf(picker(uri("3kwatch"), FakeNavigator(CombinePickerScreen(uri("3kwatch"))))) {
            awaitUntil { it.candidates != null }.eventSink(CombinePickerEvent.Pick(AtUri(uri("3kedge"))))
            assertEquals(UiError.Kind.CombineActivity, awaitUntil { it.error != null }.error?.kind)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theCombineActionOpensThePicker() = runTest {
        day()
        val navigator = FakeNavigator(ActivityDetailScreen(uri("3kwatch")))
        presenterTestOf(detail(uri("3kwatch"), navigator)) {
            val loaded = awaitLoaded()
            assertTrue(loaded.manage?.canCombine == true)
            loaded.eventSink(ActivityDetailEvent.Combine)
            assertEquals(CombinePickerScreen(uri("3kwatch")), navigator.awaitNextScreen())
            loaded.eventSink(ActivityDetailEvent.AddRecording)
            assertEquals(ImportActivityScreen(combineInto = uri("3kwatch")), navigator.awaitNextScreen(), "a file to combine with it")
            cancelAndIgnoreRemainingEvents()
        }
        presenterTestOf(detail(uri("3kother"), FakeNavigator(ActivityDetailScreen(uri("3kother"))))) {
            assertFalse(awaitLoaded().manage?.canCombine == true, "a member is combined through its combined activity")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun splittingLeavesThePage() = runTest {
        day()
        val navigator = FakeNavigator(ActivityDetailScreen(uri("3kday")))
        presenterTestOf(detail(uri("3kday"), navigator)) {
            val loaded = awaitLoaded()
            assertEquals(2, loaded.manage?.members)
            loaded.eventSink(ActivityDetailEvent.Split)
            navigator.awaitPop()
            assertTrue("delete ${Nsid.FEED_ACTIVITY}/3kday" in writes)
            assertTrue("3kother" in records && "3kmorning" in records, "members stay")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun removingAMemberRebuildsOrSplits() = runTest {
        day()
        records["3knoon"] = ride("2026-10-14T06:10:00Z", minutes = 40)
        records["3kday"] = ride("2026-10-14T06:00:00Z", members = listOf("3kother", "3kmorning", "3knoon"))
        val navigator = FakeNavigator(ActivityDetailScreen(uri("3kday")))
        presenterTestOf(detail(uri("3kday"), navigator)) {
            awaitLoaded().eventSink(ActivityDetailEvent.RemoveMember(AtUri(uri("3knoon"))))
            val rebuilt = awaitUntil { (it as? ActivityDetailState.Loaded)?.manage?.members == 2 }
            assertEquals(listOf(uri("3kother"), uri("3kmorning")), records.getValue("3kday").membersOf())

            rebuilt.eventSink(ActivityDetailEvent.RemoveMember(AtUri(uri("3kmorning"))))
            navigator.awaitPop()
            assertFalse("3kday" in records, "one member left: split")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A watch with heart rate and a bike computer with heart rate and power, combined as 3kride. */
    private fun ride() {
        val time = (0..3600 step 2).toList()
        records["3kwatch"] = ride("2026-10-14T09:00:00Z")
        records["3kedge"] = ride("2026-10-14T09:00:00Z")
        streams("3kwatch", ActivityStreamData(time = time, heartRate = time.map { 150 }))
        streams("3kedge", ActivityStreamData(time = time, heartRate = time.map { 140 }, power = time.map { 250 }))
        records["3kride"] = ride("2026-10-14T09:00:00Z", members = listOf("3kwatch", "3kedge"))
    }

    private suspend fun ReceiveTurbine<ActivityDetailState>.awaitSources() =
        (awaitUntil { (it as? ActivityDetailState.Loaded)?.dataSources?.kinds?.isNotEmpty() == true } as ActivityDetailState.Loaded)

    @Test
    fun theOwnerSeesEachKindOfDataAndWhereItComesFrom() = runTest {
        ride()
        presenterTestOf(detail(uri("3kride"), FakeNavigator(ActivityDetailScreen(uri("3kride"))))) {
            val sources = awaitSources().dataSources!!
            assertEquals(listOf(DataKind.HeartRate, DataKind.Power), sources.kinds.map { it.kind })
            val heartRate = sources.kinds.first()
            assertTrue(heartRate.included)
            assertEquals(setOf(uri("3kwatch"), uri("3kedge")), heartRate.offered.map { it.uri.value }.toSet())
            val power = sources.kinds.last()
            assertTrue(power.included, "power, recorded by one member, is included by default")
            assertEquals(uri("3kedge"), power.chosen?.uri?.value)
            assertTrue(power.offered.isEmpty(), "nothing to choose between")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anotherAthleteSeesNoDataSources() = runTest {
        ride()
        bobs += "3kride"
        presenterTestOf(detail(uri("3kride"), FakeNavigator(ActivityDetailScreen(uri("3kride"))))) {
            val loaded = awaitLoaded()
            assertEquals(null, loaded.dataSources)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun excludingThenIncludingUpdatesTheSourcesAfterEachRebuild() = runTest {
        ride()
        presenterTestOf(detail(uri("3kride"), FakeNavigator(ActivityDetailScreen(uri("3kride"))))) {
            awaitSources().eventSink(ActivityDetailEvent.SetIncluded(DataKind.Power, included = false))
            val excluded = awaitUntil { s -> s.powerRow()?.included == false && s.saving() == null }
            assertEquals(null, excluded.powerRow()?.chosen)
            assertEquals(null, streamsOf("3kride")?.power, "rebuilt without power")
            assertEquals("150|140", "${streamsOf("3kwatch")?.heartRate?.first()}|${streamsOf("3kedge")?.heartRate?.first()}")
            assertEquals(250, streamsOf("3kedge")?.power?.first(), "the member keeps its power")

            (excluded as ActivityDetailState.Loaded).eventSink(ActivityDetailEvent.SetIncluded(DataKind.Power, included = true))
            awaitUntil { s -> s.powerRow()?.included == true && s.saving() == null }
            assertEquals(250, streamsOf("3kride")?.power?.first(), "power is back")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theChartsFollowARebuildBeforeTheAppViewIndexesIt() = runTest {
        ride()
        servedStreams["3kride"] = streamsOf("3kedge")!!
        presenterTestOf(detail(uri("3kride"), FakeNavigator(ActivityDetailScreen(uri("3kride"))))) {
            val loaded = awaitUntil { it.hasPowerChart() && it.powerRow() != null } as ActivityDetailState.Loaded
            assertTrue(DetailSection.Power in loaded.sections)

            loaded.eventSink(ActivityDetailEvent.SetIncluded(DataKind.Power, included = false))
            val rebuilt = awaitUntil { s -> s.powerRow()?.included == false && s.saving() == null && !s.hasPowerChart() }
            assertTrue(DetailSection.Power !in (rebuilt as ActivityDetailState.Loaded).sections, "no Power card beside figures without it")
            assertTrue(rebuilt.charts!!.lines.any { it.kind == StreamKind.HeartRate }, "the rebuilt streams, not none")
            assertEquals(250, servedStreams.getValue("3kride").power?.first(), "the AppView still serves the old streams")

            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun ActivityDetailState.hasPowerChart() =
        (this as? ActivityDetailState.Loaded)?.charts?.lines?.any { it.kind == StreamKind.Power } == true

    @Test
    fun choosingASourceOnceRebuildsOnce() = runTest {
        ride()
        presenterTestOf(detail(uri("3kride"), FakeNavigator(ActivityDetailScreen(uri("3kride"))))) {
            val loaded = awaitSources()
            val before = writes.count { it == "put ${Nsid.FEED_ACTIVITY}/3kride" }
            gate = CompletableDeferred()
            loaded.eventSink(ActivityDetailEvent.ChooseSource(DataKind.HeartRate, AtUri(uri("3kwatch"))))
            awaitUntil { it.saving() == DataKind.HeartRate }
            loaded.eventSink(ActivityDetailEvent.ChooseSource(DataKind.HeartRate, AtUri(uri("3kwatch"))))
            gate.complete(Unit)
            val chosen = awaitUntil { s -> s.saving() == null && s.heartRateRow()?.chosen?.uri?.value == uri("3kwatch") }
            assertEquals(before + 1, writes.count { it == "put ${Nsid.FEED_ACTIVITY}/3kride" }, "a second tap does nothing")
            assertTrue(streamsOf("3kride")!!.heartRate!!.all { it == 150 })
            assertEquals(150, AtJson.decodeFromJsonElement(ActivityRecord.serializer(), records.getValue("3kride")).avgHeartRate)
            assertTrue((chosen as ActivityDetailState.Loaded).dataSources?.error == null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedRebuildShowsAnErrorAndRetries() = runTest {
        ride()
        presenterTestOf(detail(uri("3kride"), FakeNavigator(ActivityDetailScreen(uri("3kride"))))) {
            val loaded = awaitSources()
            failReads = true
            loaded.eventSink(ActivityDetailEvent.SetIncluded(DataKind.Power, included = false))
            val failed = awaitUntil { (it as? ActivityDetailState.Loaded)?.dataSources?.error != null } as ActivityDetailState.Loaded
            assertEquals(UiError.Kind.SaveDataSource, failed.dataSources?.error?.kind)
            assertEquals(true, failed.powerRow()?.included, "nothing changed")

            failReads = false
            failed.eventSink(ActivityDetailEvent.RetrySources)
            awaitUntil { s -> s.powerRow()?.included == false && (s as ActivityDetailState.Loaded).dataSources?.error == null }
            assertEquals(null, streamsOf("3kride")?.power)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A morning ride and, after lunch, an afternoon one: the afternoon was just saved. */
    private fun lunch() {
        records["3kam"] = ride("2026-10-14T09:00:00Z", minutes = 165)
        records["3kpm"] = ride("2026-10-14T13:00:00Z", minutes = 120)
    }

    private fun ActivityDetailState.suggestion() = (this as? ActivityDetailState.Loaded)?.combineSuggestion

    @Test
    fun aSaveAsksRightAwayAndNotNowLeavesABanner() = runTest {
        lunch()
        presenterTestOf(detail(uri("3kpm"), FakeNavigator(ActivityDetailScreen(uri("3kpm"), true)), suggestOnOpen = true)) {
            val loaded = awaitUntil { it.suggestion() != null } as ActivityDetailState.Loaded
            val prompt = loaded.combineSuggestion!!
            assertTrue(prompt.asPrompt)
            assertEquals(CombineCue.Break, prompt.cue)
            assertEquals(uri("3kam"), prompt.other.uri.value)

            loaded.eventSink(ActivityDetailEvent.NotNowSuggestion)
            assertFalse(awaitUntil { it.suggestion()?.asPrompt == false }.suggestion()!!.asPrompt, "a banner now")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun openingThePageLaterShowsABanner() = runTest {
        lunch()
        presenterTestOf(detail(uri("3kam"), FakeNavigator(ActivityDetailScreen(uri("3kam"))))) {
            val banner = awaitUntil { it.suggestion() != null }.suggestion()!!
            assertFalse(banner.asPrompt)
            assertEquals(uri("3kpm"), banner.other.uri.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun dontCombineHidesItOnBothPages() = runTest {
        lunch()
        val g = graph()
        presenterTestOf(detail(uri("3kpm"), FakeNavigator(ActivityDetailScreen(uri("3kpm"))), shared = g)) {
            (awaitUntil { it.suggestion() != null } as ActivityDetailState.Loaded).eventSink(ActivityDetailEvent.DontCombineSuggestion)
            awaitUntil { it is ActivityDetailState.Loaded && it.suggestion() == null }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(null, g.suggestions.forActivity(g.feed.activity(AtUri(uri("3kam")))), "nor on the morning's page")
        assertEquals(null, g.suggestions.forActivity(g.feed.activity(AtUri(uri("3kpm")))))
    }

    @Test
    fun aFollowerSeesNoSuggestion() = runTest {
        lunch()
        bobs += setOf("3kam", "3kpm")
        presenterTestOf(detail(uri("3kpm"), FakeNavigator(ActivityDetailScreen(uri("3kpm"))))) {
            assertEquals(null, awaitLoaded().combineSuggestion)
            assertEquals(0, byStartQueries, "nothing is looked for on someone else's activity")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun combiningAddsToTheCombinedDayAndOpensIt() = runTest {
        records["3km1"] = ride("2026-10-14T09:00:00Z")
        records["3km2"] = ride("2026-10-14T10:30:00Z")
        records["3kday"] = ride("2026-10-14T09:00:00Z", minutes = 150, members = listOf("3km1", "3km2"))
        records["3klate"] = ride("2026-10-14T12:00:00Z")
        val navigator = FakeNavigator(ActivityDetailScreen(uri("3km2")), ActivityDetailScreen(uri("3klate"), true))
        presenterTestOf(detail(uri("3klate"), navigator, suggestOnOpen = true)) {
            val loaded = awaitUntil { it.suggestion() != null } as ActivityDetailState.Loaded
            assertEquals(uri("3kday"), loaded.combineSuggestion!!.other.uri.value, "offered as the combined day")
            loaded.eventSink(ActivityDetailEvent.CombineSuggested)

            navigator.awaitPop()
            assertEquals(ActivityDetailScreen(uri("3kday")), navigator.awaitNextScreen())
            assertEquals(listOf("3km1", "3km2", "3klate"), records.getValue("3kday").membersOf().map { it.substringAfterLast('/') })
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun ActivityDetailState.rows() = (this as? ActivityDetailState.Loaded)?.dataSources?.kinds

    private fun ActivityDetailState.powerRow() = rows()?.firstOrNull { it.kind == DataKind.Power }

    private fun ActivityDetailState.heartRateRow() = rows()?.firstOrNull { it.kind == DataKind.HeartRate }

    private fun ActivityDetailState.saving() = (this as? ActivityDetailState.Loaded)?.dataSources?.saving

    private suspend fun ReceiveTurbine<ActivityDetailState>.awaitLoaded() =
        awaitUntil { it is ActivityDetailState.Loaded } as ActivityDetailState.Loaded
}
