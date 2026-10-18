package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.PdsLookup
import io.github.solcott.activitytracker.appview.auth.AppViewSigningKey
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveGas
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveTank
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.ActivitySensor
import io.github.solcott.activitytracker.lexicon.app.AspectRatio
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivitiesByStart
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.GetFollowers
import io.github.solcott.activitytracker.lexicon.app.GetFollows
import io.github.solcott.activitytracker.lexicon.app.GetLikes
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.SearchActors
import io.github.solcott.activitytracker.lexicon.app.SensorType
import io.github.solcott.activitytracker.lexicon.app.WaterType
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Index events the way Jetstream delivers them, then read them back through the XRPC API. */
class AppViewTest {
    private val service = "did:web:localhost%3A8081"
    private val alice = "did:plc:alice"
    private val bob = "did:plc:bob"
    private val carol = "did:plc:carol"
    private val keys = mapOf(alice to TestAccountKey(), bob to TestAccountKey())

    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val indexer = Indexer(db, { did -> did.value.substringAfterLast(':') + ".test" })
    private val config = AppViewConfig.fromEnv(emptyMap())
    private val verifier = ServiceAuthVerifier(service, keys = { keys[it.value]?.multibase })

    private fun commit(
        did: String,
        collection: String,
        rkey: String,
        record: String,
        op: String = "create",
    ) = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-09-30T12:00:00.000000Z",
                operation = op,
                collection = collection,
                rkey = rkey,
                record = if (op == "delete") null else AtJson.parseToJsonElement(record).jsonObject,
                cid = "bafy$rkey",
            )
        )
    }

    /** Bob posts a run that Alice follows, likes and comments on, plus one garbage record. Returns the run's URI. */
    private fun seedBobsRunWithAlicesFollowLikeAndComment(): String {
        val activityUri = "at://$bob/${Nsid.FEED_ACTIVITY}/3kbob1"
        commit(
            bob,
            Nsid.FEED_ACTIVITY,
            "3kbob1",
            """{"sport":"run","startedAt":"2026-09-30T10:00:00Z","elapsedTime":1800,"createdAt":"2026-09-30T10:31:00Z",""" +
                """"distance":5000,"avgTemperature":-55,"subSport":"trail"}""",
        )
        commit(bob, Nsid.ACTOR_PROFILE, "self", """{"displayName":"Bob","units":"imperial"}""")
        commit(
            alice,
            Nsid.GRAPH_FOLLOW,
            "3kf1",
            """{"subject":"$bob","createdAt":"2026-09-30T09:00:00Z"}""",
        )
        commit(
            alice,
            Nsid.FEED_LIKE,
            "3kk1",
            """{"subject":{"uri":"$activityUri","cid":"bafy3kbob1"},"createdAt":"2026-09-30T11:00:00Z"}""",
        )
        commit(
            alice,
            Nsid.FEED_COMMENT,
            "3kc1",
            """{"subject":{"uri":"$activityUri","cid":"bafy3kbob1"},"text":"Nice!","createdAt":"2026-09-30T11:01:00Z"}""",
        )
        // Garbage records are ignored rather than failing the stream.
        commit(alice, Nsid.FEED_ACTIVITY, "3kbad", """{"sport":"run"}""")
        return activityUri
    }

    @Test
    fun aRecordingShowsOnlyAmongItsActivitysRecordings() = testApplication {
        application { appView(config, db, verifier) }
        fun dive(did: String, rkey: String, startedAt: String, extra: String = "") =
            commit(
                did,
                Nsid.FEED_ACTIVITY,
                rkey,
                """{"sport":"dive","startedAt":"$startedAt","elapsedTime":2700,"createdAt":"2026-10-01T00:00:00Z"$extra}""",
            )
        val primary = "at://$alice/${Nsid.FEED_ACTIVITY}/3kp"
        dive(alice, "3kp", "2024-05-12T16:00:00Z", ""","device":"Garmin Descent Mk3"""")
        dive(alice, "3kr", "2024-05-12T16:01:00Z", ""","device":"Shearwater Perdix","recordingOf":"$primary"""")
        // A recording of a recording, and a "recording" of someone else's dive, show on their own.
        dive(alice, "3kc", "2024-05-12T16:02:00Z", ""","recordingOf":"at://$alice/${Nsid.FEED_ACTIVITY}/3kr"""")
        dive(alice, "3kx", "2024-05-12T18:00:00Z", ""","recordingOf":"at://$bob/${Nsid.FEED_ACTIVITY}/3kb"""")
        // Its activity not indexed (deleted, say): it shows on its own.
        dive(alice, "3ko", "2024-05-13T10:00:00Z", ""","recordingOf":"at://$alice/${Nsid.FEED_ACTIVITY}/3kgone"""")
        // Links the indexer won't honor: to itself, to another collection, too long to store.
        dive(alice, "3ks", "2024-05-14T10:00:00Z", ""","recordingOf":"at://$alice/${Nsid.FEED_ACTIVITY}/3ks"""")
        dive(alice, "3kl", "2024-05-15T10:00:00Z", ""","recordingOf":"at://$alice/${Nsid.FEED_LIKE}/3kp"""")
        dive(alice, "3kz", "2024-05-16T10:00:00Z", ""","recordingOf":"$primary${"x".repeat(512)}"""")
        // A recording's device is shown only as long as the view allows.
        dive(alice, "3kv", "2024-05-12T16:03:00Z", ""","device":"${"d".repeat(300)}","recordingOf":"$primary"""")

        val feed =
            AtJson.decodeFromString(
                GetAuthorActivities.Output.serializer(),
                client.get("/xrpc/${GetAuthorActivities.method.nsid}?actor=$alice").bodyAsText(),
            )
        assertEquals(listOf("3kc", "3kl", "3ko", "3kp", "3ks", "3kx", "3kz"), feed.activities.mapNotNull { it.uri.rkey }.sorted())
        val shown = feed.activities.single { it.uri.rkey == "3kp" }
        assertEquals(listOf("3kr" to "Shearwater Perdix", "3kv" to "d".repeat(256)), shown.recordings?.map { it.uri.rkey to it.device })
        assertEquals(null, feed.activities.single { it.uri.rkey == "3kx" }.recordings)

        val recording = client.get("/xrpc/${GetActivity.method.nsid}?uri=at://$alice/${Nsid.FEED_ACTIVITY}/3kr")
        assertEquals(HttpStatusCode.OK, recording.status, "a recording still opens on its own")

        commit(alice, Nsid.FEED_ACTIVITY, "3kp", "", op = "delete")
        val orphaned =
            AtJson.decodeFromString(
                GetAuthorActivities.Output.serializer(),
                client.get("/xrpc/${GetAuthorActivities.method.nsid}?actor=$alice").bodyAsText(),
            )
        assertEquals(true, orphaned.activities.any { it.uri.rkey == "3kr" }, "with its activity deleted, a recording shows on its own")
    }

    @Test
    fun authorActivitiesByStartReadsARangeOldestFirst() = testApplication {
        application { appView(config, db, verifier) }
        fun activity(did: String, rkey: String, sport: String, startedAt: String) =
            commit(
                did,
                Nsid.FEED_ACTIVITY,
                rkey,
                """{"sport":"$sport","startedAt":"$startedAt","elapsedTime":2700,"createdAt":"2026-10-01T00:00:00Z"}""",
            )
        // Posted in one go, long after they happened: the range is on startedAt, not on when they were indexed.
        activity(alice, "3kd3", "dive", "2024-05-12T20:30:00Z")
        activity(alice, "3kd1", "dive", "2024-05-12T16:02:00Z")
        activity(alice, "3kd2", "dive", "2024-05-12T16:02:00Z")
        activity(alice, "3kr1", "run", "2024-05-12T12:00:00Z")
        activity(alice, "3kd0", "dive", "2024-05-11T16:00:00Z")
        activity(alice, "3kd4", "dive", "2024-05-13T00:00:00Z")
        activity(bob, "3kb1", "dive", "2024-05-12T16:02:00Z")

        val nsid = GetAuthorActivitiesByStart.method.nsid
        suspend fun page(cursor: String?, sport: String? = "dive", limit: Int = 2): GetAuthorActivitiesByStart.Output {
            val query =
                listOfNotNull(
                    "actor=$alice",
                    "since=2024-05-12T00:00:00Z",
                    "until=2024-05-13T00:00:00Z",
                    "limit=$limit",
                    sport?.let { "sport=$it" },
                    cursor?.let { "cursor=${it.encodeURLParameter()}" },
                )
            val response = client.get("/xrpc/$nsid?${query.joinToString("&")}")
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            return AtJson.decodeFromString(GetAuthorActivitiesByStart.Output.serializer(), response.bodyAsText())
        }

        val first = page(null)
        val second = page(first.cursor)
        assertEquals(listOf("3kd1", "3kd2", "3kd3"), (first.activities + second.activities).map { it.uri.rkey })
        assertEquals(null, second.cursor, "a short page is the last")
        // One a page splits the two dives that started together: the cursor's uri breaks the tie.
        val one = page(null, limit = 1)
        val tied = page(one.cursor, limit = 1)
        assertEquals(listOf("3kd1", "3kd2"), (one.activities + tied.activities).map { it.uri.rkey })
        assertEquals(listOf("3kr1", "3kd1"), page(null, sport = null).activities.map { it.uri.rkey }, "every sport without a filter")

        val backwards = client.get("/xrpc/$nsid?actor=$alice&since=2024-05-13T00:00:00Z&until=2024-05-12T00:00:00Z")
        assertEquals(HttpStatusCode.BadRequest, backwards.status)
    }

    @Test
    fun timelineShowsFollowedActivitiesWithCounts() = testApplication {
        application { appView(config, db, verifier) }
        val activityUri = seedBobsRunWithAlicesFollowLikeAndComment()

        val nsid = GetTimeline.method.nsid
        val unauthenticated = client.get("/xrpc/$nsid")
        assertEquals(HttpStatusCode.Unauthorized, unauthenticated.status)

        val response =
            client.get("/xrpc/$nsid?limit=10") {
                header(
                    HttpHeaders.Authorization,
                    "Bearer ${keys.getValue(alice).serviceJwt(alice, service, nsid)}",
                )
            }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val timeline = AtJson.decodeFromString(GetTimeline.Output.serializer(), response.bodyAsText())
        val activity = timeline.activities.single()
        assertEquals(activityUri, activity.uri.value)
        assertEquals("Bob", activity.author.displayName)
        assertEquals("bob.test", activity.author.handle.value)
        assertEquals(1, activity.likeCount)
        assertEquals(1, activity.commentCount)
        assertNotNull(activity.viewer?.like)

        val detail = client.get("/xrpc/${GetActivity.method.nsid}?uri=$activityUri")
        val output = AtJson.decodeFromString(GetActivity.Output.serializer(), detail.bodyAsText())
        assertEquals(emptyList(), output.comments, "comments are listed by getComments")
        val comments = client.get("/xrpc/${GetComments.method.nsid}?uri=$activityUri")
        assertEquals("Nice!", AtJson.decodeFromString(GetComments.Output.serializer(), comments.bodyAsText()).comments.single().text)
        assertEquals(-55L, output.activity.record.avgTemperature)
        assertEquals("trail", output.activity.record.subSport)
        assertEquals("trail", transaction(db) { Activities.selectAll().single()[Activities.subSport] })

        val profile = client.get("/xrpc/${GetProfile.method.nsid}?actor=bob.test")
        val view = AtJson.decodeFromString(GetProfile.method.output, profile.bodyAsText())
        assertEquals(1, view.activitiesCount)
        assertEquals(1, view.followersCount)
        assertEquals("imperial", view.units)

        // Deleting the like record removes it from counts.
        commit(alice, Nsid.FEED_LIKE, "3kk1", "", op = "delete")
        val after = client.get("/xrpc/${GetActivity.method.nsid}?uri=$activityUri")
        assertEquals(
            0,
            AtJson.decodeFromString(GetActivity.Output.serializer(), after.bodyAsText()).activity.likeCount,
        )
    }

    @Test
    fun feedsFilterByTypeAndStartTimeOnTheServer() = testApplication {
        application { appView(config, db, verifier) }
        fun activity(did: String, rkey: String, type: String, startedAt: String, createdAt: String = startedAt) {
            val sport = type.substringBefore(':')
            val subSport = type.substringAfter(':', "").takeIf { it.isNotEmpty() }?.let { ""","subSport":"$it"""" }.orEmpty()
            commit(
                did,
                Nsid.FEED_ACTIVITY,
                rkey,
                """{"sport":"$sport","startedAt":"$startedAt","elapsedTime":1800,"createdAt":"$createdAt"$subSport}""",
            )
        }
        follow(alice, bob, "3kf1")
        activity(alice, "3ka1", "ride", "2026-03-05T08:00:00Z")
        activity(alice, "3ka2", "run:trail", "2026-03-10T08:00:00Z")
        activity(bob, "3kb1", "run", "2026-03-15T08:00:00Z")
        activity(bob, "3kb2", "swim", "2026-02-01T08:00:00Z")
        // Ridden in March, posted in April: the date filter is on when it started.
        activity(alice, "3ka3", "ride:gravel", "2026-03-20T08:00:00Z", createdAt = "2026-04-10T08:00:00Z")

        val nsid = GetTimeline.method.nsid
        suspend fun timeline(query: String = ""): GetTimeline.Output {
            val response =
                client.get("/xrpc/$nsid?$query") {
                    header(HttpHeaders.Authorization, "Bearer ${keys.getValue(alice).serviceJwt(alice, service, nsid)}")
                }
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            return AtJson.decodeFromString(GetTimeline.Output.serializer(), response.bodyAsText())
        }
        suspend fun rkeys(query: String = "") = timeline(query).activities.map { it.uri.rkey }

        // Newest posted first, the viewer's own included, and unchanged with no filter.
        assertEquals(listOf("3ka3", "3kb1", "3ka2", "3ka1", "3kb2"), rkeys())
        assertEquals(rkeys(), rkeys("limit=30"))
        assertEquals(listOf("3kb1", "3ka2"), rkeys("type=run"), "a sport matches every sub-sport")
        assertEquals(listOf("3ka2"), rkeys("type=run:trail"), "sport:subSport matches exactly")
        assertEquals(listOf("3ka3", "3ka2", "3ka1"), rkeys("type=ride&type=run:trail"), "any type matches")
        assertEquals(emptyList(), rkeys("type=hike"), "an unknown type matches nothing")
        assertEquals(listOf("3ka3", "3kb1"), rkeys("since=2026-03-12T00:00:00Z"))
        assertEquals(listOf("3ka3", "3kb1", "3ka2", "3ka1"), rkeys("since=2026-03-01T00:00:00Z&until=2026-04-01T00:00:00Z"))
        assertEquals(listOf("3ka1"), rkeys("type=ride&since=2026-03-01T00:00:00Z&until=2026-03-15T00:00:00Z"), "type AND date")

        // Every page of a filtered feed stays filtered, with no gaps or repeats.
        val first = timeline("type=ride&type=run&limit=2")
        val second = timeline("type=ride&type=run&limit=2&cursor=${first.cursor!!.encodeURLParameter()}")
        assertEquals(listOf("3ka3", "3kb1", "3ka2", "3ka1"), (first.activities + second.activities).map { it.uri.rkey })

        val author = client.get("/xrpc/${GetAuthorActivities.method.nsid}?actor=$alice&type=ride")
        assertEquals(
            listOf("3ka3", "3ka1"),
            AtJson.decodeFromString(GetAuthorActivities.Output.serializer(), author.bodyAsText()).activities.map { it.uri.rkey },
        )

        val tooMany = (1..21).joinToString("&") { "type=s$it" }
        val bad =
            listOf(
                tooMany,
                "type=run:",
                "type=:trail",
                "type=a:b:c",
                "type=",
                "type=${"x".repeat(130)}",
                "since=2026-03-02T00:00:00Z&until=2026-03-01T00:00:00Z",
                "since=2026-03-01T00:00:00Z&until=2026-03-01T00:00:00Z",
            )
        for (query in bad) {
            val response = client.get("/xrpc/${GetAuthorActivities.method.nsid}?actor=$alice&$query")
            assertEquals(HttpStatusCode.BadRequest, response.status, query)
            assertEquals(true, "InvalidRequest" in response.bodyAsText(), query)
        }
    }

    @Test
    fun servesARidesSensorsHeldToTheLexiconsLimits() = testApplication {
        application { appView(config, db, verifier) }
        val uri = "at://$bob/${Nsid.FEED_ACTIVITY}/3kride"
        val many = (1..20).joinToString(",") { """{"kind":"light","manufacturer":"Acme"}""" }
        commit(
            bob,
            Nsid.FEED_ACTIVITY,
            "3kride",
            """{"sport":"ride","startedAt":"2026-09-30T10:00:00Z","elapsedTime":1800,"createdAt":"2026-09-30T10:31:00Z",""" +
                """"sensors":[{"kind":"heartRate","manufacturer":"Garmin","model":"HRM 200"},""" +
                """{"kind":"power","manufacturer":"SRAM","model":"${"x".repeat(500)}"},$many]}""",
        )

        val detail = client.get("/xrpc/${GetActivity.method.nsid}?uri=$uri")
        val sensors = AtJson.decodeFromString(GetActivity.Output.serializer(), detail.bodyAsText()).activity.record.sensors!!
        assertEquals(ActivitySensor.MAX, sensors.size)
        assertEquals(ActivitySensor(SensorType.HEART_RATE, "Garmin", "HRM 200"), sensors.first())
        assertEquals(128, sensors[1].model?.length, "an overlong model is clamped")
    }

    @Test
    fun servesCountsAndTheUtcOffsetDroppingImpossibleOnes() = testApplication {
        application { appView(config, db, verifier) }
        fun commitRun(rkey: String, fields: String) =
            commit(
                bob,
                Nsid.FEED_ACTIVITY,
                rkey,
                """{"sport":"run","startedAt":"2026-09-30T10:00:00Z","elapsedTime":1800,"createdAt":"2026-09-30T10:31:00Z",$fields}""",
            )
        suspend fun record(rkey: String) =
            AtJson.decodeFromString(
                    GetActivity.Output.serializer(),
                    client.get("/xrpc/${GetActivity.method.nsid}?uri=at://$bob/${Nsid.FEED_ACTIVITY}/$rkey").bodyAsText(),
                )
                .activity
                .record
        commitRun("3krun", """"cycles":8400,"avgStrokeDistance":0,"utcOffset":120""")
        commitRun("3kodd", """"cycles":-5,"utcOffset":1200""")

        val run = record("3krun")
        assertEquals(8400, run.cycles)
        assertEquals(120, run.utcOffset)
        val odd = record("3kodd")
        assertNull(odd.cycles)
        assertNull(odd.utcOffset, "no zone is 20 hours ahead")
    }

    @Test
    fun servesAnActivitiesPhotosThroughRedirectsToThePds() = testApplication {
        application { appView(config, db, verifier, PdsLookup { "http://pds.test" }) }
        val uri = "at://$bob/${Nsid.FEED_ACTIVITY}/3kphotos"
        fun photo(cid: String, mime: String = "image/webp") =
            $$"""{"$type":"app.activitytracker.feed.activity#image","image":{"$type":"blob","ref":{"$link":"$$cid"},""" +
                """"mimeType":"$mime","size":1024},"aspectRatio":{"width":4,"height":3}}"""
        commit(
            bob,
            Nsid.FEED_ACTIVITY,
            "3kphotos",
            """{"sport":"dive","startedAt":"2026-09-30T10:00:00Z","elapsedTime":2700,"createdAt":"2026-09-30T10:50:00Z",""" +
                """"dive":{"site":"${"x".repeat(700)}"},"media":[${photo("bafkone")},""" +
                $$"""{"$type":"app.activitytracker.feed.activity#video","video":{}},$${photo("bafkgif", "image/gif")},""" +
                """${photo("bafktwo", "image/jpeg")}]}""",
        )

        val activity =
            AtJson.decodeFromString(GetActivity.Output.serializer(), client.get("/xrpc/${GetActivity.method.nsid}?uri=$uri").bodyAsText())
                .activity
        assertEquals(
            listOf("${config.publicUrl}/img/media/$bob/bafkone", "${config.publicUrl}/img/media/$bob/bafktwo"),
            activity.media.orEmpty().map { (it as ActivityMediaView.Image).fullsize },
            "only photos of an allowed type are served",
        )
        assertEquals(AspectRatio(4, 3), (activity.media?.first() as ActivityMediaView.Image).aspectRatio)
        assertEquals(ActivityDive.MAX_SITE_LENGTH, activity.record.dive?.site?.length, "an overlong site is clamped")

        val noRedirects = createClient { followRedirects = false }
        val redirect = noRedirects.get("/img/media/$bob/bafkone")
        assertEquals(HttpStatusCode.Found, redirect.status)
        assertEquals(
            "http://pds.test/xrpc/com.atproto.sync.getBlob?did=did%3Aplc%3Abob&cid=bafkone",
            redirect.headers[HttpHeaders.Location],
        )
        // Only a photo an indexed activity of that athlete shows: not another athlete's, nor one the record didn't allow.
        assertEquals(HttpStatusCode.NotFound, noRedirects.get("/img/media/$alice/bafkone").status)
        assertEquals(HttpStatusCode.NotFound, noRedirects.get("/img/media/$bob/bafkgif").status)

        commit(bob, Nsid.FEED_ACTIVITY, "3kphotos", "", op = "delete")
        assertEquals(HttpStatusCode.NotFound, noRedirects.get("/img/media/$bob/bafkone").status)
    }

    @Test
    fun servesADivesSummaryHeldToTheLexiconsLimits() = testApplication {
        application { appView(config, db, verifier) }
        val uri = "at://$bob/${Nsid.FEED_ACTIVITY}/3kdive"
        val gases = (1..15).joinToString(",") { """{"oxygen":32,"helium":0}""" }
        val tanks = (1..12).joinToString(",") { """{"startPressure":200000,"endPressure":60000}""" }
        commit(
            bob,
            Nsid.FEED_ACTIVITY,
            "3kdive",
            """{"sport":"dive","startedAt":"2026-09-30T10:00:00Z","elapsedTime":2700,"createdAt":"2026-09-30T10:50:00Z",""" +
                """"dive":{"maxDepth":3012,"gases":[{"oxygen":250,"helium":-5},$gases],"tanks":[$tanks]}}""",
        )

        val detail = client.get("/xrpc/${GetActivity.method.nsid}?uri=$uri")
        val dive = AtJson.decodeFromString(GetActivity.Output.serializer(), detail.bodyAsText()).activity.record.dive!!
        assertEquals(3012, dive.maxDepth)
        assertEquals(ActivityDive.MAX_GASES, dive.gases?.size)
        assertEquals(ActivityDiveGas(100, 0), dive.gases?.first(), "out-of-range percents are clamped")
        assertEquals(ActivityDive.MAX_TANKS, dive.tanks?.size)
    }

    @Test
    fun servesADivesNewerSummaryAndARidesPowerAsRecorded() = testApplication {
        application { appView(config, db, verifier) }
        commit(
            bob,
            Nsid.FEED_ACTIVITY,
            "3kdive",
            """{"sport":"dive","startedAt":"2026-09-30T10:00:00Z","elapsedTime":2700,"createdAt":"2026-09-30T10:50:00Z",""" +
                """"normalizedPower":5,"dive":{"endN2":64,"waterType":"salt","highestPpO2":1400,""" +
                """"tanks":[{"volume":12000,"gas":{"oxygen":320,"helium":0}}]}}""",
        )

        val detail = client.get("/xrpc/${GetActivity.method.nsid}?uri=at://$bob/${Nsid.FEED_ACTIVITY}/3kdive")
        val record = AtJson.decodeFromString(GetActivity.Output.serializer(), detail.bodyAsText()).activity.record
        assertEquals(5, record.normalizedPower)
        assertEquals(64, record.dive?.endN2)
        assertEquals(WaterType.SALT, record.dive?.waterType)
        assertEquals(1400, record.dive?.highestPpO2)
        assertEquals(
            ActivityDiveTank(volume = 12_000, gas = ActivityDiveGas(100, 0)),
            record.dive?.tanks?.single(),
            "a tank's gas is clamped",
        )
    }

    @Test
    fun likesCountAthletesAndIgnoreSelfLikes() = testApplication {
        application { appView(config, db, verifier) }
        val activityUri = seedBobsRunWithAlicesFollowLikeAndComment()
        val like = """{"subject":{"uri":"$activityUri","cid":"bafy3kbob1"},"createdAt":"2026-09-30T11:05:00Z"}"""
        // A second like record from Alice, and Bob liking his own run: both written by other clients, neither counts.
        commit(alice, Nsid.FEED_LIKE, "3kk2", like)
        commit(bob, Nsid.FEED_LIKE, "3kk3", like)

        val detail = client.get("/xrpc/${GetActivity.method.nsid}?uri=$activityUri")
        assertEquals(1, AtJson.decodeFromString(GetActivity.Output.serializer(), detail.bodyAsText()).activity.likeCount)
    }

    @Test
    fun listsLikingAthletesOnce() = testApplication {
        application { appView(config, db, verifier) }
        val activityUri = seedBobsRunWithAlicesFollowLikeAndComment()
        val like = """{"subject":{"uri":"$activityUri","cid":"bafy3kbob1"},"createdAt":"2026-09-30T11:05:00Z"}"""
        commit(alice, Nsid.FEED_LIKE, "3kk2", like)
        commit(carol, Nsid.FEED_LIKE, "3kk3", like)
        commit(bob, Nsid.FEED_LIKE, "3kk4", like)

        suspend fun list(query: String): GetLikes.Output {
            val response = client.get("/xrpc/${GetLikes.method.nsid}?$query")
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            return AtJson.decodeFromString(GetLikes.Output.serializer(), response.bodyAsText())
        }

        val all = list("uri=${activityUri.encodeURLParameter()}")
        assertEquals(activityUri, all.uri.value)
        // Alice's duplicate is listed once and Bob's self-like isn't indexed.
        assertEquals(setOf(alice, carol), all.likes.map { it.did.value }.toSet())
        assertEquals(2, all.likes.size)
        assertEquals("carol.test", all.likes.single { it.did.value == carol }.handle.value)

        // limit=1 walks every athlete through the cursor exactly once, despite Alice's duplicate.
        val paged = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page =
                list("uri=${activityUri.encodeURLParameter()}&limit=1" + cursor?.let { "&cursor=${it.encodeURLParameter()}" }.orEmpty())
            paged += page.likes.map { it.did.value }
            cursor = page.cursor
        } while (cursor != null)
        assertEquals(listOf(alice, carol).sorted(), paged.sorted())

        commit(carol, Nsid.FEED_LIKE, "3kk3", "", op = "delete")
        assertEquals(listOf(alice), list("uri=${activityUri.encodeURLParameter()}").likes.map { it.did.value })

        val missing = client.get("/xrpc/${GetLikes.method.nsid}?uri=${"at://$bob/${Nsid.FEED_ACTIVITY}/nope".encodeURLParameter()}")
        assertEquals(HttpStatusCode.BadRequest, missing.status)
        assertEquals(true, missing.bodyAsText().contains("NotFound"))
    }

    private fun follow(author: String, subject: String, rkey: String) =
        commit(author, Nsid.GRAPH_FOLLOW, rkey, """{"subject":"$subject","createdAt":"2026-09-30T09:00:00Z"}""")

    @Test
    fun listsFollowersAndFollowsWithPagination() = testApplication {
        application { appView(config, db, verifier) }
        follow(alice, bob, "3kf1")
        follow(carol, bob, "3kf2")
        follow(bob, alice, "3kf3")
        commit(bob, Nsid.ACTOR_PROFILE, "self", """{"displayName":"Bob"}""")

        suspend fun followers(query: String): GetFollowers.Output {
            val response = client.get("/xrpc/${GetFollowers.method.nsid}?$query")
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            return AtJson.decodeFromString(GetFollowers.Output.serializer(), response.bodyAsText())
        }

        val all = followers("actor=bob.test")
        assertEquals("Bob", all.subject.displayName)
        assertEquals(setOf(alice, carol), all.followers.map { it.did.value }.toSet())
        assertEquals("carol.test", all.followers.single { it.did.value == carol }.handle.value)

        // limit=1 walks every follower through the cursor, without repeats.
        val paged = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = followers("actor=$bob&limit=1" + cursor?.let { "&cursor=${it.encodeURLParameter()}" }.orEmpty())
            paged += page.followers.map { it.did.value }
            cursor = page.cursor
        } while (cursor != null)
        assertEquals(setOf(alice, carol), paged.toSet())
        assertEquals(2, paged.size)

        val follows = client.get("/xrpc/${GetFollows.method.nsid}?actor=bob.test")
        assertEquals(
            listOf(alice),
            AtJson.decodeFromString(GetFollows.Output.serializer(), follows.bodyAsText()).follows.map { it.did.value },
        )

        // Unfollowing removes the row.
        commit(carol, Nsid.GRAPH_FOLLOW, "3kf2", "", op = "delete")
        assertEquals(listOf(alice), followers("actor=bob.test").followers.map { it.did.value })

        val unknown = client.get("/xrpc/${GetFollowers.method.nsid}?actor=nobody.test")
        assertEquals(HttpStatusCode.BadRequest, unknown.status)
        assertEquals(true, unknown.bodyAsText().contains("ActorNotFound"))
    }

    @Test
    fun searchFindsOnlyThisAppsAthletesBestMatchFirst() = testApplication {
        application { appView(config, db, verifier) }
        val malia = "did:plc:malia"
        follow(alice, bob, "3kf1")
        commit(bob, Nsid.ACTOR_PROFILE, "self", """{"displayName":"Bob Alvarez"}""")
        commit(carol, Nsid.ACTOR_PROFILE, "self", """{"displayName":"Carol Smith"}""")
        commit(malia, Nsid.ACTOR_PROFILE, "self", """{"displayName":"Malia"}""")
        // Dave is only the subject of a follow: an atproto account that has never used this app.
        follow(alice, "did:plc:dave", "3kf2")

        val nsid = SearchActors.method.nsid
        val token = keys.getValue(alice).serviceJwt(alice, service, nsid)

        suspend fun search(query: String): SearchActors.Output {
            val response = client.get("/xrpc/$nsid?$query") { header(HttpHeaders.Authorization, "Bearer $token") }
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            return AtJson.decodeFromString(SearchActors.Output.serializer(), response.bodyAsText())
        }

        suspend fun handles(q: String) = search("q=${q.encodeURLParameter()}").actors.map { it.handle.value }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/xrpc/$nsid?q=bob").status)

        // A handle prefix, then a word in a display name, then a match anywhere.
        assertEquals(listOf("alice.test", "bob.test", "malia.test"), handles("al"))
        assertEquals(listOf("alice.test"), handles(" @ALICE.test "))
        assertEquals(listOf("carol.test"), handles("smith"))
        assertEquals(listOf("carol.test"), handles(carol))
        assertEquals("Bob Alvarez", search("q=bob").actors.single().displayName)
        assertEquals(emptyList(), handles("dave"))
        // LIKE wildcards in the query match only themselves.
        assertEquals(emptyList(), handles("%"))
        assertEquals(emptyList(), handles("a_i"))
        assertEquals(emptyList(), handles("  "))

        // limit=1 walks every match through the cursor in rank order.
        val paged = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = search("q=al&limit=1" + cursor?.let { "&cursor=${it.encodeURLParameter()}" }.orEmpty())
            paged += page.actors.map { it.handle.value }
            cursor = page.cursor
        } while (cursor != null)
        assertEquals(listOf("alice.test", "bob.test", "malia.test"), paged)
    }

    @Test
    fun didDocumentPublishesTheSigningKey() = testApplication {
        val key = AppViewSigningKey(TEST_SIGNING_KEY)
        application { appView(config.copy(signingKey = TEST_SIGNING_KEY), db, verifier) }
        val doc = AtJson.parseToJsonElement(client.get("/.well-known/did.json").bodyAsText()).jsonObject
        val method = (doc.getValue("verificationMethod") as JsonArray).single().jsonObject
        assertEquals("${config.serviceDid}#atproto", method.getValue("id").jsonPrimitive.content)
        assertEquals("Multikey", method.getValue("type").jsonPrimitive.content)
        assertEquals(config.serviceDid, method.getValue("controller").jsonPrimitive.content)
        assertEquals(key.publicMultibase, method.getValue("publicKeyMultibase").jsonPrimitive.content)
        val ids = (doc.getValue("service") as JsonArray).map { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals(listOf("#activity_appview", "#activitytracker_space", "#activitytracker_space_syncer"), ids)
    }

    @Test
    fun servesDidDocumentAndClientMetadata() = testApplication {
        application { appView(config, db, verifier) }
        val doc = AtJson.parseToJsonElement(client.get("/.well-known/did.json").bodyAsText()) as JsonObject
        assertEquals(config.serviceDid, doc["id"]?.toString()?.trim('"'))
        val services =
            (doc["service"] as JsonArray)
                .map { it as JsonObject }
                .associate { s ->
                    s.getValue("id").jsonPrimitive.content to
                        (s.getValue("type").jsonPrimitive.content to s.getValue("serviceEndpoint").jsonPrimitive.content)
                }
        assertEquals("ActivityTrackerAppView" to config.publicUrl, services["#activity_appview"])
        assertEquals("AtprotoSpaceService" to config.publicUrl, services["#activitytracker_space"])
        assertEquals("AtprotoSpaceSyncer" to config.publicUrl, services["#activitytracker_space_syncer"])
        // Without a signing key there's nothing for a PDS to verify delegation tokens against.
        assertEquals(null, doc["verificationMethod"])
        val metadata = client.get("/oauth/client-metadata.json").bodyAsText()
        assertEquals(true, metadata.contains("\"dpop_bound_access_tokens\":true"))
        val scope = AtJson.parseToJsonElement(metadata).jsonObject.getValue("scope").jsonPrimitive.content
        assertEquals(OAuthClientConfig.DEFAULT_SCOPE, scope)
        assertEquals(true, scope.split(' ').any { it.startsWith("space:app.activitytracker.space.athlete?authority=*&") })
    }
}
