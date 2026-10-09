package io.github.solcott.activitytracker.app

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.PdsActivityStreamsStore
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalKudos
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivitiesByStart
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.time.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A signed-in session for Alice against a fake PDS that records every `putRecord`, and a fake AppView that serves her profile as indexed
 * before any of those writes.
 */
class SessionTestFixture {
    val did = Did("did:plc:alice")
    var failWrites = false

    /** Alice's profile record as JSON, both in her repo and as the AppView indexed it; null for none. */
    var profileRecord: String? = null

    /** What the AppView's `getPrivateDetails` returns, as JSON. */
    var privateDetails: String = """{"details":{},"visibility":{}}"""
    private val putBodies = mutableListOf<String>()

    /** Alice's activities as the AppView's `getAuthorActivitiesByStart` serves them, whatever the range; null to fail. */
    var ownActivities: List<ActivityView>? = emptyList()

    /** What `getActivityStreams` serves for any activity; null for none. */
    var activityStreams: ActivityStreamData? = null

    /** What `getRecord` serves for any of Alice's activities, as JSON. */
    var activityRecord: String = "{}"

    private val privateBodies = mutableListOf<String>()

    /** The input of each successful `putPrivateDetails`, in order. */
    val privateDetailsPuts: List<JsonObject>
        get() = privateBodies.map { AtJson.parseToJsonElement(it).jsonObject }

    /** The rkey of each successful `putRecord`, in order. */
    val putRkeys: List<String>
        get() = putBodies.map { AtJson.parseToJsonElement(it).jsonObject.getValue("rkey").jsonPrimitive.content }

    /** The `record` of each successful `putRecord`, in order. */
    val putRecords: List<JsonObject>
        get() = putBodies.map { AtJson.parseToJsonElement(it).jsonObject.getValue("record").jsonObject }

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.repo.getRecord" -> {
                        val record = if (request.url.parameters["collection"] == Nsid.FEED_ACTIVITY) activityRecord else profileRecord
                        if (record == null) {
                            respond("""{"error":"RecordNotFound"}""", HttpStatusCode.BadRequest, json)
                        } else {
                            respond("""{"uri":"at://$did/app.activitytracker.actor.profile/self","value":$record}""", headers = json)
                        }
                    }

                    "/xrpc/com.atproto.repo.uploadBlob" -> {
                        respond(
                            """{"blob":{"${'$'}type":"blob","ref":{"${'$'}link":"bafkavatar"},"mimeType":"image/jpeg","size":3}}""",
                            headers = json,
                        )
                    }

                    "/xrpc/${Nsid.FEED_GET_AUTHOR_ACTIVITIES_BY_START}" -> {
                        val activities = ownActivities
                        if (activities == null) {
                            respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        } else {
                            respond(
                                AtJson.encodeToString(
                                    GetAuthorActivitiesByStart.Output.serializer(),
                                    GetAuthorActivitiesByStart.Output(activities),
                                ),
                                headers = json,
                            )
                        }
                    }

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY_STREAMS}" -> {
                        val data = activityStreams
                        if (data == null) {
                            respond("""{"error":"NotFound"}""", HttpStatusCode.BadRequest, json)
                        } else {
                            val uri = AtUri(requireNotNull(request.url.parameters["uri"]))
                            val output = GetActivityStreams.Output(uri, Visibility.PUBLIC, emptyList(), data)
                            respond(AtJson.encodeToString(GetActivityStreams.Output.serializer(), output), headers = json)
                        }
                    }

                    "/xrpc/com.atproto.server.getServiceAuth" -> {
                        respond("""{"token":"service-jwt"}""", headers = json)
                    }

                    "/xrpc/app.activitytracker.actor.getPrivateDetails" -> {
                        respond(privateDetails, headers = json)
                    }

                    "/xrpc/app.activitytracker.actor.putPrivateDetails" -> {
                        if (failWrites) {
                            respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        } else {
                            privateBodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                            respond("{}", headers = json)
                        }
                    }

                    "/xrpc/app.activitytracker.actor.getProfile" -> {
                        val view = AtJson.parseToJsonElement(profileRecord ?: "{}").jsonObject
                        val fields =
                            listOfNotNull(
                                """"did":"$did"""",
                                """"handle":"alice.test"""",
                                view["displayName"]?.let { """"displayName":$it""" },
                                view["description"]?.let { """"description":$it""" },
                            )
                        respond(fields.joinToString(",", "{", "}"), headers = json)
                    }

                    "/xrpc/com.atproto.repo.putRecord" -> {
                        if (failWrites) {
                            respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        } else {
                            putBodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                            respond("""{"uri":"at://$did/x/y","cid":"bafyrec"}""", headers = json)
                        }
                    }

                    else -> {
                        respond("", HttpStatusCode.NotFound)
                    }
                }
            }
        ) {
            expectSuccess = false
        }

    class Repos(
        val activities: ActivityRepository,
        val profiles: ProfileRepository,
        val units: UnitsRepository,
        val auth: AuthRepository,
        val recentTypes: RecentActivityTypes,
        val feed: FeedRepository,
    )

    suspend fun repos(defaultUnits: UnitSystem = UnitSystem.Metric): Repos {
        val (db, manager, clients) = signedInTestSession(http, did = did)
        val localKudos = LocalKudos()
        val feed = FeedRepository(db, clients, localKudos, Logger)
        val recentTypes = RecentActivityTypes(db, clients, feed)
        val activities =
            ActivityRepository(
                db,
                clients,
                OutboxProcessor(db, clients, Logger),
                OutboxWriter(db, clients, Clock.System),
                PdsActivityStreamsStore(OutboxWriter(db, clients, Clock.System), clients, Clock.System),
                feed,
                localKudos,
                recentTypes,
                Clock.System,
            )
        val profiles = ProfileRepository(clients, Clock.System)
        val units = UnitsRepository(profiles, Clock.System, Logger, defaultUnits)
        return Repos(activities, profiles, units, AuthRepository(manager, db, http), recentTypes, feed)
    }
}
