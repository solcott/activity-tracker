package io.github.solcott.activitytracker.app

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.app.record.ActivityTypeChoices
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityPlaces
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.CombineSuggestions
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.activity.RouteTrimmer
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.moderation.ModerationRepository
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.segments.SegmentPrefsRepository
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivitiesByStart
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.time.Clock
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A signed-in session for Alice against a fake PDS that records every `putRecord`, and a fake AppView that serves her profile as indexed
 * before any of those writes.
 */
class SessionTestFixture {
    val did = Did("did:plc:alice")
    var failWrites = false

    /** Alice's followers as the AppView lists them. */
    var followers = listOf<String>()

    /** The `writes` of each `applyWrites`, in order. */
    val appliedWrites = mutableListOf<JsonObject>()

    /** Answer every Spaces call as a session signed in before the `space:` scope. */
    var scopeMissing = false

    /** The OAuth scope the session was granted. */
    var scope = OAuthClientConfig.DEFAULT_SCOPE

    /** Every request fails as a dropped connection would, while set. */
    var offline = false

    /** Alice's profile record as JSON, both in her repo and as the AppView indexed it; null for none. */
    var profileRecord: String? = null

    /** How many times Alice's profile record has been read from her repo. */
    var profileReads = 0
        private set

    /** Alice's tier spaces: each tier's `privateDetails` record's `details`, or none. */
    private val tiers = mutableMapOf<String, JsonObject>()

    /**
     * Alice's non-public details as `{"details":{…},"visibility":{…}}`, as the edit screen sees them: each detail is put in the tier space
     * its visibility names (Only me when it has none).
     */
    var privateDetails: String
        get() = error("write-only")
        set(value) {
            val json = AtJson.parseToJsonElement(value).jsonObject
            val details = json.getValue("details").jsonObject
            val visibility = json["visibility"]?.jsonObject.orEmpty()
            tiers.clear()
            for ((field, detail) in details) {
                val group = if (field == "height") "weight" else field
                val tier = visibility[group]?.jsonPrimitive?.content ?: AthleteSpace.SELF
                tiers[tier] = JsonObject(tiers[tier].orEmpty() + (field to detail))
            }
        }

    private val putBodies = mutableListOf<String>()

    /** Alice's activities as the AppView's `getAuthorActivitiesByStart` serves them, whatever the range; null to fail. */
    var ownActivities: List<ActivityView>? = emptyList()

    /** What `getActivity` serves for any activity; null for none. */
    var servedActivity: ActivityView? = null

    /** What `getActivityStreams` serves for any activity; null for none. */
    var activityStreams: ActivityStreamData? = null

    /** What `getRecord` serves for any of Alice's activities, as JSON. */
    var activityRecord: String = "{}"

    private val tierPuts = mutableListOf<JsonObject>()
    private val otherSpacePuts = mutableListOf<Pair<String, JsonObject>>()

    /** Alice's `actor.activityDefaults` record in her `self` space, as JSON; null for none. Saving one replaces it. */
    var activityDefaults: String? = null

    /** Alice's `actor.privacyZones` record in her `self` space, as JSON; null for none. Saving one replaces it. */
    var privacyZones: String? = null

    /** Alice's `actor.segmentPrefs` record in her `self` space, as JSON; null for none. Saving one replaces it. */
    var segmentPrefs: String? = null

    /** Reads of her privacy zones fail, as offline. */
    var failZoneReads = false

    /** Each successful put to a tier space of anything but private details (activities, streams, defaults), as (tier, record), in order. */
    val spacePuts: List<Pair<String, JsonObject>>
        get() = otherSpacePuts.toList()

    /** Each successful put of a tier's record, in order, as `{"details":{…},"visibility":"<tier>"}`. */
    val privateDetailsPuts: List<JsonObject>
        get() = tierPuts.toList()

    /** Each tier's details now, as JSON; absent for a tier with no record. */
    val tierDetails: Map<String, JsonObject>
        get() = tiers.toMap()

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
                if (offline) throw IOException("Connection refused")
                if (scopeMissing && "/xrpc/com.atproto.space" in request.url.encodedPath) {
                    return@MockEngine respond("""{"error":"ScopeMissingError"}""", HttpStatusCode.Forbidden, json)
                }
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.repo.getRecord" -> {
                        val record = if (request.url.parameters["collection"] == Nsid.FEED_ACTIVITY) activityRecord else profileRecord
                        if (request.url.parameters["collection"] != Nsid.FEED_ACTIVITY) profileReads++
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

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY}" -> {
                        val view = servedActivity
                        if (view == null) {
                            respond("""{"error":"NotFound"}""", HttpStatusCode.BadRequest, json)
                        } else {
                            respond(AtJson.encodeToString(GetActivity.Output.serializer(), GetActivity.Output(view)), headers = json)
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

                    "/.well-known/did.json" -> {
                        respond(
                            """{"id":"did:web:appview.test","service":[
                               {"id":"#activitytracker_space","type":"AtprotoSpaceService","serviceEndpoint":"http://appview.test"}]}""",
                            headers = json,
                        )
                    }

                    "/xrpc/com.atproto.simplespace.createSpace" -> {
                        val tier = AtJson.parseToJsonElement(body(request)).jsonObject.getValue("skey").jsonPrimitive.content
                        respond("""{"uri":"${space(tier)}"}""", headers = json)
                    }

                    "/xrpc/com.atproto.space.getRecord" -> {
                        if (request.url.parameters["collection"] == Nsid.ACTOR_PRIVACY_ZONES) {
                            if (failZoneReads)
                                return@MockEngine respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                            val value =
                                privacyZones ?: return@MockEngine respond("""{"error":"RecordNotFound"}""", HttpStatusCode.BadRequest, json)
                            return@MockEngine respond(
                                """{"uri":"at://$did/${Nsid.ACTOR_PRIVACY_ZONES}/self","cid":"bafyrec","value":$value}""",
                                headers = json,
                            )
                        }
                        if (request.url.parameters["collection"] == Nsid.ACTOR_SEGMENT_PREFS) {
                            val value =
                                segmentPrefs ?: return@MockEngine respond("""{"error":"RecordNotFound"}""", HttpStatusCode.BadRequest, json)
                            return@MockEngine respond(
                                """{"uri":"at://$did/${Nsid.ACTOR_SEGMENT_PREFS}/self","cid":"bafyrec","value":$value}""",
                                headers = json,
                            )
                        }
                        if (request.url.parameters["collection"] == Nsid.ACTOR_ACTIVITY_DEFAULTS) {
                            val value =
                                activityDefaults
                                    ?: return@MockEngine respond("""{"error":"RecordNotFound"}""", HttpStatusCode.BadRequest, json)
                            return@MockEngine respond(
                                """{"uri":"at://$did/${Nsid.ACTOR_ACTIVITY_DEFAULTS}/self","cid":"bafyrec","value":$value}""",
                                headers = json,
                            )
                        }
                        val tier = request.url.parameters["space"].orEmpty().substringAfterLast('/')
                        val details = tiers[tier]
                        if (details == null) {
                            respond("""{"error":"RecordNotFound"}""", HttpStatusCode.BadRequest, json)
                        } else {
                            val value = """{"${'$'}type":"${Nsid.ACTOR_PRIVATE_DETAILS}","details":$details}"""
                            respond(
                                """{"uri":"at://$did/${Nsid.ACTOR_PRIVATE_DETAILS}/self","cid":"bafyrec","value":$value}""",
                                headers = json,
                            )
                        }
                    }

                    "/xrpc/com.atproto.space.putRecord",
                    "/xrpc/com.atproto.space.deleteRecord" -> {
                        if (failWrites) {
                            respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        } else {
                            val input = AtJson.parseToJsonElement(body(request)).jsonObject
                            val tier = input.getValue("space").jsonPrimitive.content.substringAfterLast('/')
                            val record = input["record"]?.jsonObject
                            val collection = input.getValue("collection").jsonPrimitive.content
                            if (collection == Nsid.ACTOR_ACTIVITY_DEFAULTS) activityDefaults = record?.toString()
                            if (collection == Nsid.ACTOR_PRIVACY_ZONES) privacyZones = record?.toString()
                            if (collection == Nsid.ACTOR_SEGMENT_PREFS) segmentPrefs = record?.toString()
                            if (collection != Nsid.ACTOR_PRIVATE_DETAILS) {
                                record?.let { otherSpacePuts += tier to it }
                            } else if (record == null) {
                                tiers.remove(tier)
                            } else {
                                val details = record.getValue("details").jsonObject
                                tiers[tier] = details
                                tierPuts += JsonObject(mapOf("details" to details, "visibility" to JsonPrimitive(tier)))
                            }
                            respond("""{"uri":"at://$did/${Nsid.ACTOR_PRIVATE_DETAILS}/self","cid":"bafyrec"}""", headers = json)
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

                    "/xrpc/${Nsid.GRAPH_GET_FOLLOWERS}" -> {
                        val accounts = followers.joinToString(",") { """{"did":"$it","handle":"${it.substringAfterLast(':')}.test"}""" }
                        respond("""{"subject":{"did":"$did","handle":"alice.test"},"followers":[$accounts]}""", headers = json)
                    }

                    "/xrpc/com.atproto.repo.listRecords",
                    "/xrpc/com.atproto.space.listRecords" -> {
                        respond("""{"records":[]}""", headers = json)
                    }

                    "/xrpc/com.atproto.repo.applyWrites" -> {
                        if (failWrites) {
                            respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        } else {
                            appliedWrites += AtJson.parseToJsonElement(body(request)).jsonObject
                            respond("{}", headers = json)
                        }
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

    private fun body(request: HttpRequestData) = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private fun space(tier: String) = "at://$did/space/${Nsid.SPACE_ATHLETE}/$tier"

    @Suppress("LongParameterList") // One per repository a presenter test needs.
    class Repos(
        val activities: ActivityRepository,
        val profiles: ProfileRepository,
        val units: UnitsRepository,
        val auth: AuthRepository,
        val recentTypes: RecentActivityTypes,
        val feed: FeedRepository,
        val defaults: ActivityDefaults,
        val privacy: PrivacyZones,
        val manager: ActivityManager,
        val suggestions: CombineSuggestions,
        val blocks: BlockRepository,
        val segmentPrefs: SegmentPrefsRepository,
    ) {
        /** The type picker's producer, as Metro injects it into the record, manual entry and import presenters. */
        val typeChoices: ActivityTypeChoices
            get() = ActivityTypeChoices(recentTypes)
    }

    /** The moderator side for another session on the same fake services; with no [labelerDid] the athlete can't moderate. */
    suspend fun moderation(labelerDid: Did? = null) =
        ModerationRepository(signedInTestSession(http, did = did, labelerDid = labelerDid).clients)

    @Suppress("LongMethod") // Wires every store a test reaches for.
    suspend fun repos(defaultUnits: UnitSystem = UnitSystem.Metric, suggestionClock: Clock = Clock.System): Repos {
        val (db, manager, clients) = signedInTestSession(http, did = did, scope = scope)
        val localLikes = LocalLikes()
        val feed = FeedRepository(db, clients, localLikes, Logger)
        val recentTypes = RecentActivityTypes(db, clients, feed)
        val zones = TestZones(db, clients)
        val privacy = PrivacyZones(clients, Clock.System, Logger)
        val places = ActivityPlaces(clients, Clock.System, Logger)
        val activities =
            ActivityRepository(
                db,
                clients,
                zones.outbox,
                zones.writer,
                zones.streams,
                zones.tracks,
                zones.sources,
                zones.devices,
                zones.store,
                places,
                privacy,
                RouteTrimmer(db, clients, zones.writer, zones.outbox, privacy, zones.tracks, feed, Clock.System, Logger),
                feed,
                localLikes,
                recentTypes,
                Clock.System,
                testSegments(db, clients, zones.writer, zones.outbox, privacy, zones.tracks).tracks,
                testSegments(db, clients, zones.writer, zones.outbox, privacy, zones.tracks).sweeper,
            )
        val profiles = ProfileRepository(clients, Clock.System, Logger, zones.store)
        val units = UnitsRepository(profiles, Clock.System, Logger, defaultUnits)
        val activityManager =
            ActivityManager(
                db,
                clients,
                zones.outbox,
                zones.writer,
                zones.streams,
                zones.tracks,
                zones.sources,
                zones.devices,
                zones.store,
                places,
                feed,
                privacy,
                Clock.System,
                activities,
                testSegments(db, clients, zones.writer, zones.outbox, privacy, zones.tracks).tracks,
            )
        return Repos(
            activities,
            profiles,
            units,
            AuthRepository(manager, db, http),
            recentTypes,
            feed,
            ActivityDefaults(clients),
            privacy,
            activityManager,
            CombineSuggestions(db, clients, feed, zones.tracks, suggestionClock),
            BlockRepository(clients, zones.writer, zones.outbox, feed, Clock.System),
            segmentsOf(clients).prefs,
        )
    }
}
