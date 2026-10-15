package io.github.solcott.activitytracker.data

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestLogWriter
import co.touchlab.kermit.loggerConfigInit
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityEdit
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.ActivityZonesStore
import io.github.solcott.activitytracker.data.activity.HeldPhotos
import io.github.solcott.activitytracker.data.activity.MediaEdit
import io.github.solcott.activitytracker.data.activity.NewPhoto
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.data.activity.PdsActivityStreamsStore
import io.github.solcott.activitytracker.data.activity.cid
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ActivityViewerState
import io.github.solcott.activitytracker.lexicon.app.AspectRatio
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.PowerZoneModel
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.RecordingView
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.ZoneSnapshot
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@ExperimentalKermitApi
class ActivityRepositoryTest {
    private val did = Did("did:plc:alice")
    private var failWrites = false
    private val putBodies = mutableListOf<String>()
    private val deleteBodies = mutableListOf<String>()

    /** Every write the PDS took, in order, as "put|delete collection/rkey". */
    private val writes = mutableListOf<String>()

    /** What the AppView's `getTimeline` serves. */
    private var timeline: List<ActivityView> = emptyList()

    /** What `getRecord` serves for any activity, as JSON. */
    private var storedRecord: String = "{}"

    /** The record the AppView's `getActivity` serves for any activity. */
    private var indexedRecord: ActivityRecord? = null

    /** The session's feed, once [repository] made it. */
    private lateinit var feed: FeedRepository

    /** Deletes and ungroups, once [repository] made it. */
    private lateinit var manager: ActivityManager

    /** What the AppView's `getActivityStreams` serves; null for none. */
    private var storedStreams: GetActivityStreams.Output? = null
    private val logs = TestLogWriter(Severity.Verbose)
    private val logger = Logger(loggerConfigInit(logs))

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.repo.uploadBlob" -> {
                        val mime = request.body.contentType?.toString() ?: request.headers["Content-Type"].orEmpty()
                        if (mime.startsWith("image/")) {
                            writes += "upload $mime"
                            val link = "bafkphoto${writes.count { it.startsWith("upload") }}"
                            respond(
                                $$"""{"blob":{"$type":"blob","ref":{"$link":"$$link"},"mimeType":"$$mime","size":10}}""",
                                headers = json,
                            )
                        } else {
                            val gpx = """"mimeType":"application/gpx+xml","size":10"""
                            respond($$"""{"blob":{"$type":"blob","ref":{"$link":"bafyblob"},$$gpx}}""", headers = json)
                        }
                    }

                    "/xrpc/com.atproto.repo.putRecord" -> {
                        if (failWrites) {
                            respond(
                                """{"error":"InternalServerError"}""",
                                HttpStatusCode.InternalServerError,
                                json,
                            )
                        } else {
                            putBodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                            writes += "put " + putBodies.last().target()
                            respond("""{"uri":"at://$did/x/y","cid":"bafyrec"}""", headers = json)
                        }
                    }

                    "/xrpc/com.atproto.repo.getRecord" -> {
                        respond(
                            """{"uri":"at://$did/${Nsid.FEED_ACTIVITY}/3kexisting","cid":"bafyold","value":$storedRecord}""",
                            headers = json,
                        )
                    }

                    "/xrpc/com.atproto.server.getServiceAuth" -> {
                        respond("""{"token":"service-jwt"}""", headers = json)
                    }

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY_STREAMS}" -> {
                        val streams = storedStreams
                        if (streams == null) {
                            respond("""{"error":"NotFound"}""", HttpStatusCode.BadRequest, json)
                        } else {
                            respond(AtJson.encodeToString(GetActivityStreams.Output.serializer(), streams), headers = json)
                        }
                    }

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY}" -> {
                        val view = activityBy(did).copy(record = requireNotNull(indexedRecord))
                        respond(
                            AtJson.encodeToString(GetActivity.Output.serializer(), GetActivity.Output(view, emptyList())),
                            headers = json,
                        )
                    }

                    "/xrpc/${Nsid.FEED_GET_TIMELINE}" -> {
                        respond(AtJson.encodeToString(GetTimeline.Output.serializer(), GetTimeline.Output(timeline)), headers = json)
                    }

                    "/xrpc/com.atproto.repo.deleteRecord" -> {
                        deleteBodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                        writes += "delete " + deleteBodies.last().target()
                        respond("{}", headers = json)
                    }

                    else -> {
                        respond("", HttpStatusCode.NotFound)
                    }
                }
            }
        ) {
            expectSuccess = false
        }

    /** A session without the `space:` scope: these tests are about the repo; where tier spaces come in is `ActivityVisibilityTest`. */
    private suspend fun repository(): ActivityRepository {
        val (db, _, clients) = signedInTestSession(http, did = did, logger = logger, scope = "atproto transition:generic")
        val outbox = OutboxProcessor(db, clients, logger)
        val localLikes = LocalLikes()
        feed = FeedRepository(db, clients, localLikes, logger)
        val writer = OutboxWriter(db, clients, Clock.System)
        val zones = ActivityZonesStore(db, writer, outbox, clients, Clock.System, logger)
        val streams = PdsActivityStreamsStore(writer, clients, Clock.System, zones)
        manager = ActivityManager(db, clients, outbox, writer, streams, feed)
        return ActivityRepository(
            db,
            clients,
            outbox,
            writer,
            streams,
            zones,
            feed,
            localLikes,
            RecentActivityTypes(db, clients, feed),
            Clock.System,
        )
    }

    private fun String.target(): String {
        val body = AtJson.parseToJsonElement(this).jsonObject
        return "${body["collection"]?.jsonPrimitive?.content}/${body["rkey"]?.jsonPrimitive?.content}"
    }

    private fun activityBy(author: Did) =
        ActivityView(
            uri = AtUri.of(author, Nsid.FEED_ACTIVITY, "3kact1"),
            cid = Cid("bafyact"),
            author = ProfileViewBasic(author, Handle("someone.test")),
            record =
                ActivityRecord(
                    sport = "run",
                    startedAt = Instant.parse("2026-09-30T07:00:00Z"),
                    elapsedTime = 1800,
                    createdAt = Instant.parse("2026-09-30T07:31:00Z"),
                ),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse("2026-09-30T07:32:00Z"),
            viewer = ActivityViewerState(),
        )

    @Test
    fun publishUploadsTrackThenWritesRecordAndRetriesAfterFailure() = runTest {
        val repo = repository()

        val draft =
            ActivityDraft(
                type = ActivityType(Sport.RUN, SubSport.TRAIL),
                title = "Morning run",
                description = null,
                startedAt = Instant.parse("2026-09-30T07:00:00Z"),
                elapsed = 30.minutes,
                distanceMeters = 5012.4,
                gpx = "<gpx/>".encodeToByteArray(),
            )

        failWrites = true
        val uri = repo.publish(draft)
        assertEquals(1, repo.pendingWrites.first())
        logs.assertCount(1)
        logs.assertLast { severity == Severity.Warn && tag == "OutboxProcessor" && "1 pending" in message }

        failWrites = false
        assertEquals(0, repo.sync())
        assertEquals(0, repo.pendingWrites.first())

        val put = AtJson.parseToJsonElement(putBodies.single()).jsonObject
        assertEquals(uri.rkey, put["rkey"]?.jsonPrimitive?.content)
        val record = put["record"]!!.jsonObject
        assertEquals(Nsid.FEED_ACTIVITY, record["\$type"]?.jsonPrimitive?.content)
        assertEquals("5012", record["distance"]?.jsonPrimitive?.content)
        assertEquals(SubSport.TRAIL, record["subSport"]?.jsonPrimitive?.content)
        assertTrue("bafyblob" in record["track"].toString())
    }

    @Test
    fun publishAllQueuesEveryDraftBeforeSendingAny() = runTest {
        val repo = repository()
        fun dive(hour: Int) =
            ActivityDraft(
                type = ActivityType(Sport.DIVE, SubSport.SINGLE_GAS),
                title = "Dive $hour",
                description = null,
                startedAt = Instant.parse("2024-05-12T${hour}:00:00Z"),
                elapsed = 45.minutes,
            )

        failWrites = true
        val uris = repo.publishAll(listOf(dive(10), dive(14), dive(18)))
        assertEquals(3, uris.distinct().size)
        assertEquals(3, repo.pendingWrites.first(), "all queued, none lost to the failed upload")

        failWrites = false
        assertEquals(0, repo.sync())
        val titles = putBodies.map { AtJson.parseToJsonElement(it).jsonObject["record"]!!.jsonObject["title"]?.jsonPrimitive?.content }
        assertEquals(listOf("Dive 10", "Dive 14", "Dive 18"), titles)
        assertEquals(uris.map { it.rkey }, putBodies.map { AtJson.parseToJsonElement(it).jsonObject["rkey"]?.jsonPrimitive?.content })
    }

    private val existingDive =
        ActivityRecord(
            sport = Sport.DIVE,
            startedAt = Instant.parse("2024-05-12T16:00:00Z"),
            elapsedTime = 2700,
            createdAt = Instant.parse("2024-05-13T00:00:00Z"),
            title = "Blue Hole",
            description = "Sharks!",
            subSport = SubSport.SINGLE_GAS,
            avgTemperature = 245,
        )

    /** The same dive from a second computer, whose clock started 10 s later. */
    private val secondComputer =
        ActivityDraft(
            type = ActivityType(Sport.DIVE, SubSport.MULTI_GAS),
            title = "Morning Dive",
            description = null,
            startedAt = Instant.parse("2024-05-12T16:00:10Z"),
            elapsed = 44.minutes,
            avgTemperatureCelsius = 26.0,
            device = "Shearwater Perdix",
            streams = ActivityStreamData(time = listOf(0, 10, 20), temperature = listOf(260, 255, 250), depth = listOf(100, 900, 1800)),
        )

    @Test
    fun mergeFillsOnlyTheGapsAndAddsTheStreamsChannelsMissing() = runTest {
        val repo = repository()
        val uri = AtUri.of(did, Nsid.FEED_ACTIVITY, "3kexisting")
        storedRecord = AtJson.encodeToString(ActivityRecord.serializer(), existingDive)
        storedStreams =
            GetActivityStreams.Output(
                uri,
                Visibility.PUBLIC,
                listOf(StreamChannel.DEPTH),
                ActivityStreamData(time = listOf(0, 10, 20, 30), depth = listOf(0, 500, 1000, 1500)),
            )

        repo.mergeInto(OwnActivity(uri, Cid("bafyold"), existingDive), secondComputer)
        assertEquals(0, repo.sync())

        val (activity, streams) = putBodies.map { AtJson.parseToJsonElement(it).jsonObject }
        assertEquals("3kexisting", activity["rkey"]?.jsonPrimitive?.content)
        val merged = AtJson.decodeFromJsonElement(ActivityRecord.serializer(), activity["record"]!!)
        assertEquals(existingDive.copy(device = "Shearwater Perdix"), merged, "only the device was missing")
        assertEquals("3kexisting", streams["rkey"]?.jsonPrimitive?.content)
        val record = AtJson.decodeFromJsonElement(ActivityStreamsRecord.serializer(), streams["record"]!!)
        assertFalse("visibility" in streams.getValue("record").jsonObject, "the repo is who may see them")
        assertEquals(listOf(StreamChannel.TEMPERATURE, StreamChannel.DEPTH), record.channels)
    }

    @Test
    fun aMergeShowsOverTheAppViewsStaleCopyUntilItCatchesUp() = runTest {
        val repo = repository()
        val uri = activityBy(did).uri
        storedRecord = AtJson.encodeToString(ActivityRecord.serializer(), existingDive)
        indexedRecord = existingDive

        repo.mergeInto(OwnActivity(uri, Cid("bafyold"), existingDive), secondComputer)
        val merged = existingDive.copy(device = "Shearwater Perdix")
        assertEquals(merged, feed.activity(uri).record, "Jetstream hasn't brought the AppView the merge yet")

        indexedRecord = merged
        assertEquals(merged, feed.activity(uri).record)
        // Caught up, the AppView's copy is the one shown from now on, whatever later changes it.
        indexedRecord = existingDive.copy(title = "Renamed elsewhere")
        assertEquals("Renamed elsewhere", feed.activity(uri).record.title)
    }

    @Test
    fun mergeIntoAnActivityStillInTheOutboxLeavesItsStreams() = runTest {
        val repo = repository()
        val uri = AtUri.of(did, Nsid.FEED_ACTIVITY, "3kpending")

        repo.mergeInto(OwnActivity(uri, cid = null, existingDive.copy(subSport = null)), secondComputer)
        repo.sync()

        val record = AtJson.parseToJsonElement(putBodies.single()).jsonObject["record"]!!
        assertEquals(SubSport.MULTI_GAS, AtJson.decodeFromJsonElement(ActivityRecord.serializer(), record).subSport)
    }

    @Test
    fun publishWritesStreamsUnderTheActivitysRkeyAfterIt() = runTest {
        val repo = repository()
        val draft =
            ActivityDraft(
                type = ActivityType(Sport.RIDE),
                title = null,
                description = null,
                startedAt = Instant.parse("2026-09-30T07:00:00Z"),
                elapsed = 30.minutes,
                streams = ActivityStreamData(time = listOf(0, 1), power = listOf(200, 210)),
                streamsVisibility = Visibility.PUBLIC,
                powerZones = ZoneSnapshot(PowerZoneModel.COGGAN_6, listOf(0, 138, 188, 225, 263, 300)),
            )

        val uri = repo.publish(draft)

        val (activity, streams) = putBodies.map { AtJson.parseToJsonElement(it).jsonObject }
        assertEquals(Nsid.FEED_ACTIVITY, activity["collection"]?.jsonPrimitive?.content)
        assertEquals(Nsid.FEED_ACTIVITY_STREAMS, streams["collection"]?.jsonPrimitive?.content)
        assertEquals(uri.rkey, streams["rkey"]?.jsonPrimitive?.content)
        val record = AtJson.decodeFromJsonElement(ActivityStreamsRecord.serializer(), streams["record"]!!)
        assertEquals(uri, record.activity)
        assertFalse("visibility" in streams.getValue("record").jsonObject, "the repo is who may see them")
        assertEquals(listOf(StreamChannel.POWER), record.channels)
        assertEquals("bafyblob", record.data?.ref?.link)
        assertFalse("powerZones" in streams.getValue("record").jsonObject, "zones are a record of their own")
    }

    @Test
    fun oversizedStreamsAreHalvedUntilTheyFit() {
        // Four days at one sample a second.
        val hours = 60 * 60 * 24 * 4
        val data =
            ActivityStreamData(
                time = List(hours) { it },
                distance = List(hours) { it * 800 },
                power = List(hours) { 250 },
                heartRate = List(hours) { 150 },
            )
        val bytes = PdsActivityStreamsStore.encode(data)
        assertTrue(bytes.size <= ActivityStreamsRecord.MAX_DATA_BYTES)
        val decoded = AtJson.decodeFromString(ActivityStreamData.serializer(), bytes.decodeToString())
        assertTrue(decoded.time.size < hours && decoded.power!!.size == decoded.time.size)
    }

    @Test
    fun rapidLikeTapsOnAStaleSnapshotNeverWriteTwoLikes() = runTest {
        val repo = repository()
        // Every tap carries the same snapshot, taken before the first like landed.
        val stale = activityBy(Did("did:plc:bob"))

        listOf(launch { repo.toggleLike(stale) }, launch { repo.toggleLike(stale) }).joinAll()
        repo.toggleLike(stale)

        assertEquals(2, putBodies.size)
        assertEquals(1, deleteBodies.size)
        val rkeys = putBodies.map { AtJson.parseToJsonElement(it).jsonObject["rkey"]?.jsonPrimitive?.content }
        assertEquals(rkeys.first(), AtJson.parseToJsonElement(deleteBodies.single()).jsonObject["rkey"]?.jsonPrimitive?.content)
        assertTrue(putBodies.all { Nsid.FEED_LIKE in it })
    }

    @Test
    fun ownActivitiesCannotGetLikes() = runTest {
        val repo = repository()

        repo.toggleLike(activityBy(did))

        assertTrue(putBodies.isEmpty())
        assertEquals(0, repo.pendingWrites.first())
    }

    private fun ownActivity(rkey: String) = activityBy(did).copy(uri = AtUri.of(did, Nsid.FEED_ACTIVITY, rkey))

    private fun recording(rkey: String) = RecordingView(AtUri.of(did, Nsid.FEED_ACTIVITY, rkey), Instant.parse("2026-09-30T07:00:00Z"))

    @Test
    fun deletingAnActivityDeletesItsStreamsAndKeepsItOutOfTheFeedUntilTheAppViewCatchesUp() = runTest {
        repository()
        val activity = ownActivity("3kgone")
        timeline = listOf(activity, ownActivity("3kkept"))
        feed.refreshTimeline()

        manager.delete(activity, withRecordings = false)

        assertEquals(
            listOf(
                "delete ${Nsid.FEED_ACTIVITY}/3kgone",
                "delete ${Nsid.FEED_ACTIVITY_STREAMS}/3kgone",
                "delete ${Nsid.FEED_ACTIVITY_ZONES}/3kgone",
            ),
            writes,
        )
        assertEquals(listOf("3kkept"), feed.timeline().first().map { it.uri.rkey })
        feed.refreshTimeline()
        assertEquals(listOf("3kkept"), feed.timeline().first().map { it.uri.rkey }, "Jetstream hasn't brought the AppView the delete yet")
    }

    @Test
    fun deletingAGroupWithItsRecordingsDeletesEveryOne() = runTest {
        repository()

        manager.delete(ownActivity("3kmain").copy(recordings = listOf(recording("3krec1"), recording("3krec2"))), withRecordings = true)

        assertEquals(
            listOf("3krec1", "3krec2", "3kmain").flatMap {
                listOf(
                    "delete ${Nsid.FEED_ACTIVITY}/$it",
                    "delete ${Nsid.FEED_ACTIVITY_STREAMS}/$it",
                    "delete ${Nsid.FEED_ACTIVITY_ZONES}/$it",
                )
            },
            writes,
        )
    }

    @Test
    fun deletingOnlyTheMainActivitySplitsItsRecordingsOffFirst() = runTest {
        repository()
        val main = ownActivity("3kmain")
        storedRecord =
            AtJson.encodeToString(
                ActivityRecord.serializer(),
                existingDive.copy(
                    recordingOf = main.uri,
                    track = BlobRef(ref = BlobRef.Link("bafytrack"), mimeType = "application/gpx+xml", size = 10),
                ),
            )

        manager.delete(main.copy(recordings = listOf(recording("3krec1"))), withRecordings = false)

        assertEquals(
            listOf(
                "put ${Nsid.FEED_ACTIVITY}/3krec1",
                "delete ${Nsid.FEED_ACTIVITY}/3kmain",
                "delete ${Nsid.FEED_ACTIVITY_STREAMS}/3kmain",
                "delete ${Nsid.FEED_ACTIVITY_ZONES}/3kmain",
            ),
            writes,
        )
        val record =
            AtJson.decodeFromJsonElement(ActivityRecord.serializer(), AtJson.parseToJsonElement(putBodies.single()).jsonObject["record"]!!)
        assertEquals(null, record.recordingOf)
        assertEquals("bafytrack", record.track?.ref?.link, "the recording keeps its track")
    }

    @Test
    fun ungroupingDropsTheRecordingFromItsMainActivity() = runTest {
        repository()
        val main = ownActivity("3kmain").copy(recordings = listOf(recording("3krec1"), recording("3krec2")))
        timeline = listOf(main)
        feed.refreshTimeline()
        storedRecord = AtJson.encodeToString(ActivityRecord.serializer(), existingDive.copy(recordingOf = main.uri))

        manager.ungroup(recording("3krec1").uri, from = main.uri)

        assertEquals(listOf("put ${Nsid.FEED_ACTIVITY}/3krec1"), writes)
        assertEquals(listOf("3krec2"), feed.timeline().first().single().recordings?.map { it.uri.rkey })
        feed.refreshTimeline()
        assertEquals(listOf("3krec2"), feed.timeline().first().single().recordings?.map { it.uri.rkey }, "until the AppView catches up")
    }

    private fun photoBlob(cid: String) = BlobRef(ref = BlobRef.Link(cid), mimeType = "image/webp", size = 10)

    private val newPhoto = NewPhoto(byteArrayOf(1, 2, 3), "image/webp", width = 4, height = 3)

    @Test
    fun editUploadsOnlyNewPhotosAndKeepsTheTrackAndUnknownMedia() = runTest {
        repository()
        val video = AtJson.parseToJsonElement($$"""{"$type":"app.activitytracker.feed.activity#video","video":{}}""").jsonObject
        val track = BlobRef(ref = BlobRef.Link("bafytrack"), mimeType = "application/gpx+xml", size = 10)
        storedRecord =
            AtJson.encodeToString(
                ActivityRecord.serializer(),
                existingDive.copy(
                    track = track,
                    media =
                        listOf(
                            ActivityMedia.Image(photoBlob("bafkold1")),
                            ActivityMedia.Unknown(video),
                            ActivityMedia.Image(photoBlob("bafkold2")),
                        ),
                ),
            )
        val activity = ownActivity("3kdive").copy(media = listOf(ActivityMediaView.Image("https://appview/img/media/$did/bafkold2")))
        timeline = listOf(activity)
        feed.refreshTimeline()

        manager.edit(
            activity,
            ActivityEdit(
                title = " Blue Hole North ",
                description = "",
                diveSite = "Blue Hole",
                media = listOf(MediaEdit.Keep("bafkold2"), MediaEdit.Add(newPhoto)),
            ),
        )

        assertEquals(listOf("upload image/webp", "put ${Nsid.FEED_ACTIVITY}/3kdive"), writes)
        val record =
            AtJson.decodeFromJsonElement(
                ActivityRecord.serializer(),
                AtJson.parseToJsonElement(putBodies.single()).jsonObject.getValue("record"),
            )
        assertEquals("Blue Hole North", record.title)
        assertEquals(null, record.description)
        assertEquals("Blue Hole", record.dive?.site)
        assertEquals(existingDive.dive?.maxDepth, record.dive?.maxDepth)
        assertEquals(track, record.track)
        assertEquals(
            listOf(
                ActivityMedia.Image(photoBlob("bafkold2")),
                ActivityMedia.Image(photoBlob("bafkphoto1"), aspectRatio = AspectRatio(4, 3)),
                ActivityMedia.Unknown(video),
            ),
            record.media,
        )

        // The AppView still serves the old record: the kept photo loads from it, the new one from the bytes this device uploaded.
        feed.refreshTimeline()
        val shown = feed.timeline().first().single()
        assertEquals("Blue Hole North", shown.record.title)
        val urls = shown.media.orEmpty().map { (it as ActivityMediaView.Image).fullsize }
        assertEquals(listOf("https://appview/img/media/$did/bafkold2", HeldPhotos.url("bafkphoto1")), urls)
        assertEquals(newPhoto, feed.heldPhotos["bafkphoto1"])

        // Once the AppView serves the new photo, its URL wins and the bytes go.
        val served = listOf("bafkold2", "bafkphoto1").map { ActivityMediaView.Image("https://appview/img/media/$did/$it") }
        timeline = listOf(activity.copy(record = record, media = served))
        feed.refreshTimeline()
        assertEquals(served, feed.timeline().first().single().media)
        assertEquals(null, feed.heldPhotos["bafkphoto1"])
    }

    @Test
    fun publishUploadsPhotosBeforeWritingTheRecord() = runTest {
        val repo = repository()
        val draft =
            ActivityDraft(
                type = ActivityType(Sport.RUN),
                title = null,
                description = "Hills",
                startedAt = Instant.parse("2026-09-30T07:00:00Z"),
                elapsed = 30.minutes,
                photos = listOf(newPhoto, newPhoto),
            )

        repo.publish(draft)

        assertEquals(listOf("upload image/webp", "upload image/webp"), writes.take(2))
        assertTrue(writes[2].startsWith("put ${Nsid.FEED_ACTIVITY}/"))
        val record =
            AtJson.decodeFromJsonElement(
                ActivityRecord.serializer(),
                AtJson.parseToJsonElement(putBodies.single()).jsonObject.getValue("record"),
            )
        assertEquals("Hills", record.description)
        assertEquals(listOf("bafkphoto1", "bafkphoto2"), record.media.orEmpty().map { it.cid })
        assertEquals(newPhoto, feed.heldPhotos["bafkphoto1"], "held until the AppView serves it")
    }
}
