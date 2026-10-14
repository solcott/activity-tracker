package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActivityDefaultsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveGas
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveTank
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivitySensor
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CommentRecord
import io.github.solcott.activitytracker.lexicon.app.FollowApprovalRecord
import io.github.solcott.activitytracker.lexicon.app.FollowDeclineRecord
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetFollowRequests
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.HeartRateZoneModel
import io.github.solcott.activitytracker.lexicon.app.LikeRecord
import io.github.solcott.activitytracker.lexicon.app.Location
import io.github.solcott.activitytracker.lexicon.app.NotifySpace
import io.github.solcott.activitytracker.lexicon.app.PrivateDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.SensorType
import io.github.solcott.activitytracker.lexicon.app.SpeedZoneModel
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SportTraining
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.ThresholdEntry
import io.github.solcott.activitytracker.lexicon.app.ThresholdSource
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.TrainingSetAt
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.ApplyWrites
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class RecordSerializationTest {
    private val record =
        ActivityRecord(
            sport = Sport.RUN,
            startedAt = Instant.parse("2026-09-30T14:00:00Z"),
            elapsedTime = 1800,
            createdAt = Instant.parse("2026-09-30T14:31:00Z"),
            distance = 5000,
        )

    @Test
    fun recordCarriesTypeAndOmitsNulls() {
        val json = AtJson.encodeToJsonElement(ActivityRecord.serializer(), record).jsonObject
        assertEquals(Nsid.FEED_ACTIVITY, json["\$type"]?.jsonPrimitive?.content)
        assertEquals("2026-09-30T14:00:00Z", json["startedAt"]?.jsonPrimitive?.content)
        assertFalse("title" in json)
    }

    @Test
    fun roundTripsAndIgnoresUnknownFields() {
        val json =
            """
            {"${'$'}type":"${Nsid.FEED_ACTIVITY}","sport":"run","startedAt":"2026-09-30T14:00:00Z",
             "elapsedTime":1800,"createdAt":"2026-09-30T14:31:00Z","distance":5000,"futureField":true}
            """
                .trimIndent()
        assertEquals(record, AtJson.decodeFromString(ActivityRecord.serializer(), json))
    }

    @Test
    fun subSportRoundTripsAndOlderRecordsLackIt() {
        val mtb = record.copy(sport = Sport.RIDE, subSport = SubSport.MOUNTAIN)
        val json = AtJson.encodeToString(ActivityRecord.serializer(), mtb)
        assertTrue("\"subSport\":\"mountain\"" in json)
        assertEquals(mtb, AtJson.decodeFromString(ActivityRecord.serializer(), json))
        assertFalse("subSport" in AtJson.encodeToString(ActivityRecord.serializer(), record))
    }

    @Test
    fun recordingOfRoundTripsAndOlderRecordsLackIt() {
        val recording = record.copy(recordingOf = AtUri("at://did:plc:alice/${Nsid.FEED_ACTIVITY}/3kprimary"))
        val json = AtJson.encodeToString(ActivityRecord.serializer(), recording)
        assertTrue("\"recordingOf\":\"at://did:plc:alice/${Nsid.FEED_ACTIVITY}/3kprimary\"" in json, json)
        assertEquals(recording, AtJson.decodeFromString(ActivityRecord.serializer(), json))
        assertFalse("recordingOf" in AtJson.encodeToString(ActivityRecord.serializer(), record))
    }

    @Test
    fun sensorsRoundTripAndOlderRecordsLackThem() {
        val ride =
            record.copy(
                sport = Sport.RIDE,
                sensors = listOf(ActivitySensor(SensorType.HEART_RATE, "Garmin", "HRM 200"), ActivitySensor(SensorType.POWER, "SRAM")),
            )
        val json = AtJson.encodeToString(ActivityRecord.serializer(), ride)
        assertTrue("""{"kind":"power","manufacturer":"SRAM"}""" in json, "a missing model is omitted: $json")
        assertEquals(ride, AtJson.decodeFromString(ActivityRecord.serializer(), json))
        assertFalse("sensors" in AtJson.encodeToString(ActivityRecord.serializer(), record))
    }

    @Test
    fun diveRoundTripsAndOmitsItsNulls() {
        val dive =
            record.copy(
                sport = Sport.DIVE,
                dive =
                    ActivityDive(
                        maxDepth = 3012,
                        minTemperature = -15,
                        gases = listOf(ActivityDiveGas(32, 0)),
                        tanks = listOf(ActivityDiveTank(startPressure = 200_000, endPressure = 60_000)),
                    ),
            )
        val json = AtJson.encodeToString(ActivityRecord.serializer(), dive)
        assertTrue(""""dive":{"maxDepth":3012,"minTemperature":-15,"gases":[{"oxygen":32,"helium":0}]""" in json, json)
        assertFalse("avgDepth" in json || "volumeUsed" in json, json)
        assertEquals(dive, AtJson.decodeFromString(ActivityRecord.serializer(), json))
        assertFalse("\"dive\"" in AtJson.encodeToString(ActivityRecord.serializer(), record))
    }

    @Test
    fun negativeTemperatureRoundTrips() {
        val cold = record.copy(avgTemperature = -55)
        val json = AtJson.encodeToString(ActivityRecord.serializer(), cold)
        assertTrue("\"avgTemperature\":-55" in json)
        assertEquals(cold, AtJson.decodeFromString(ActivityRecord.serializer(), json))
    }

    @Test
    fun paramsRoundTripThroughQueryString() {
        val params = GetTimeline.Params(limit = 25, cursor = "abc")
        val encoded = XrpcParams.encode(GetTimeline.Params.serializer(), params)
        assertEquals(listOf("limit" to "25", "cursor" to "abc"), encoded)
        val decoded =
            XrpcParams.decode(
                GetTimeline.Params.serializer(),
                encoded.groupBy({ it.first }, { it.second }),
            )
        assertEquals(params, decoded)
        assertTrue(XrpcParams.encode(GetTimeline.Params.serializer(), GetTimeline.Params()).isEmpty())
    }

    @Test
    fun filterParamsRoundTripThroughQueryString() {
        val since = Instant.parse("2026-03-01T07:00:00Z")
        val until = Instant.parse("2026-04-01T06:00:00Z")
        val params = GetAuthorActivities.Params("alice.test", type = listOf("ride", "run:trail"), since = since, until = until)
        val encoded = XrpcParams.encode(GetAuthorActivities.Params.serializer(), params)
        assertEquals(
            listOf(
                "actor" to "alice.test",
                "type" to "ride",
                "type" to "run:trail",
                "since" to "2026-03-01T07:00:00Z",
                "until" to "2026-04-01T06:00:00Z",
            ),
            encoded,
        )
        assertEquals(params, XrpcParams.decode(GetAuthorActivities.Params.serializer(), encoded.groupBy({ it.first }, { it.second })))
    }

    @Test
    fun singleArrayParamDecodesToList() {
        val decoded = XrpcParams.decode(GetTimeline.Params.serializer(), mapOf("type" to listOf("ride")))
        assertEquals(listOf("ride"), decoded.type)
        assertEquals(null, XrpcParams.decode(GetTimeline.Params.serializer(), emptyMap()).type)
    }

    @Test
    fun tidsAreSortableAndUnique() {
        val tids = List(100) { Tid.next() }
        assertEquals(tids.sorted(), tids)
        assertEquals(tids.size, tids.toSet().size)
        assertTrue(tids.all { it.length == 13 })
    }

    @Test
    fun atUriParts() {
        val uri = AtUri.of(Did("did:plc:abc"), Nsid.FEED_ACTIVITY, "3k2")
        assertEquals("did:plc:abc", uri.authority)
        assertEquals(Nsid.FEED_ACTIVITY, uri.collection)
        assertEquals("3k2", uri.rkey)
    }

    @Test
    fun profileDetailsAndAvatarRoundTrip() {
        val profile =
            ProfileRecord(
                displayName = "Alice",
                avatar = BlobRef(ref = BlobRef.Link("bafkavatar"), mimeType = "image/jpeg", size = 1234),
                details =
                    ProfileDetails(
                        location = Location(locality = "Portland", country = "US"),
                        weight = 61_500,
                        height = 1_750,
                        training =
                            TrainingDetails(
                                functionalThresholdPower = 231,
                                heartRateZoneModel = HeartRateZoneModel.COGGAN,
                                autoUpdate = listOf(Threshold.FUNCTIONAL_THRESHOLD_POWER),
                                setAt = TrainingSetAt(functionalThresholdPower = Instant.parse("2026-10-01T07:00:00Z")),
                                sports =
                                    listOf(
                                        SportTraining(
                                            sport = Sport.RUN,
                                            thresholdSpeed = 3271,
                                            speedZoneModel = SpeedZoneModel.FRIEL_RUNNING,
                                            setAt = TrainingSetAt(thresholdSpeed = Instant.parse("2026-10-02T07:00:00Z")),
                                        )
                                    ),
                            ),
                    ),
            )
        val json = AtJson.encodeToJsonElement(ProfileRecord.serializer(), profile).jsonObject
        assertEquals("blob", json["avatar"]?.jsonObject?.get("\$type")?.jsonPrimitive?.content)
        val details = json.getValue("details").jsonObject
        assertEquals("61500", details["weight"]?.jsonPrimitive?.content)
        assertEquals("1750", details["height"]?.jsonPrimitive?.content)
        assertFalse("birthDate" in details)
        assertFalse("region" in details.getValue("location").jsonObject)
        val training = details.getValue("training").jsonObject
        assertEquals("231", training["functionalThresholdPower"]?.jsonPrimitive?.content)
        assertFalse("maxHeartRate" in training)
        assertEquals("2026-10-01T07:00:00Z", training.getValue("setAt").jsonObject["functionalThresholdPower"]?.jsonPrimitive?.content)
        val run = training.getValue("sports").jsonArray.single().jsonObject
        assertEquals("run", run["sport"]?.jsonPrimitive?.content)
        assertEquals("3271", run["thresholdSpeed"]?.jsonPrimitive?.content)
        assertFalse("thresholdHeartRate" in run)
        assertEquals("2026-10-02T07:00:00Z", run.getValue("setAt").jsonObject["thresholdSpeed"]?.jsonPrimitive?.content)
        assertEquals(profile, AtJson.decodeFromJsonElement(ProfileRecord.serializer(), json))
    }

    @Test
    fun privateDetailsRoundTrip() {
        val record = PrivateDetailsRecord(ProfileDetails(birthDate = "1990-04-01", weight = 61_500))
        val json = AtJson.encodeToJsonElement(PrivateDetailsRecord.serializer(), record).jsonObject
        assertEquals(Nsid.ACTOR_PRIVATE_DETAILS, json["\$type"]?.jsonPrimitive?.content)
        val details = json.getValue("details").jsonObject
        assertEquals("1990-04-01", details["birthDate"]?.jsonPrimitive?.content)
        assertFalse("location" in details)
        assertEquals(record, AtJson.decodeFromJsonElement(PrivateDetailsRecord.serializer(), json))
    }

    @Test
    fun activityDefaultsRoundTripAndResolve() {
        val record = ActivityDefaultsRecord(visibility = Visibility.MUTUALS, chartsVisibility = Visibility.SELF)
        val json = AtJson.encodeToJsonElement(ActivityDefaultsRecord.serializer(), record).jsonObject
        assertEquals(Nsid.ACTOR_ACTIVITY_DEFAULTS, json["\$type"]?.jsonPrimitive?.content)
        assertEquals("mutuals", json["visibility"]?.jsonPrimitive?.content)
        assertEquals(record, AtJson.decodeFromJsonElement(ActivityDefaultsRecord.serializer(), json))
        assertEquals(Visibility.MUTUALS to Visibility.SELF, record.resolved())

        val empty = AtJson.decodeFromString(ActivityDefaultsRecord.serializer(), """{"${'$'}type":"${Nsid.ACTOR_ACTIVITY_DEFAULTS}"}""")
        assertEquals(Visibility.PUBLIC to Visibility.PUBLIC, empty.resolved())
        val wideCharts = ActivityDefaultsRecord(visibility = Visibility.FOLLOWERS, chartsVisibility = Visibility.PUBLIC)
        assertEquals(Visibility.FOLLOWERS to Visibility.FOLLOWERS, wideCharts.resolved())
        assertEquals(Visibility.SELF to Visibility.SELF, ActivityDefaultsRecord(visibility = "friends").resolved())
    }

    @Test
    fun visibilityOrder() {
        assertEquals(Visibility.MUTUALS, Visibility.narrower(Visibility.FOLLOWERS, Visibility.MUTUALS))
        assertEquals(Visibility.SELF, Visibility.narrower(Visibility.SELF, Visibility.PUBLIC))
        assertEquals(listOf(Visibility.MUTUALS, Visibility.SELF), Visibility.atMost(Visibility.MUTUALS))
        assertEquals(Visibility.all, Visibility.atMost(Visibility.PUBLIC))
        assertEquals(Visibility.all.lastIndex, Visibility.rank("friends"))
    }

    @Test
    fun activityViewVisibilityIsOptional() {
        val base =
            """{"uri":"at://did:plc:alice/${Nsid.FEED_ACTIVITY}/3k","cid":"bafyrei",""" +
                """"author":{"did":"did:plc:alice","handle":"alice.test"},""" +
                """"record":{"sport":"run","startedAt":"2026-09-30T14:00:00Z","elapsedTime":1800,"createdAt":"2026-09-30T14:31:00Z"},""" +
                """"likeCount":0,"commentCount":0,"indexedAt":"2026-09-30T14:31:00Z""""
        val old = AtJson.decodeFromString(ActivityView.serializer(), "$base}")
        assertEquals(null, old.visibility)
        assertEquals(Visibility.PUBLIC, old.visibilityOrPublic)
        val followers = AtJson.decodeFromString(ActivityView.serializer(), "$base,\"visibility\":\"followers\"}")
        assertEquals(Visibility.FOLLOWERS, followers.visibilityOrPublic)
        val unknown = AtJson.decodeFromString(ActivityView.serializer(), "$base,\"visibility\":\"friends\"}")
        assertEquals(Visibility.SELF, unknown.visibilityOrPublic)
    }

    @Test
    fun notifySpaceInputRoundTrips() {
        val input = NotifySpace.Input(SpaceUri("at://did:plc:alice/space/${Nsid.SPACE_ATHLETE}/followers"))
        val json = AtJson.encodeToJsonElement(NotifySpace.Input.serializer(), input).jsonObject
        assertEquals("at://did:plc:alice/space/${Nsid.SPACE_ATHLETE}/followers", json["space"]?.jsonPrimitive?.content)
        assertEquals(input, AtJson.decodeFromJsonElement(NotifySpace.Input.serializer(), json))
        assertEquals("app.activitytracker.space.notifySpace", NotifySpace.method.nsid)
    }

    @Test
    fun likeRecordCarriesItsTypeAndRoundTrips() {
        val subject = StrongRef(AtUri("at://did:plc:bob/${Nsid.FEED_ACTIVITY}/3kact"), Cid("bafy3kact"))
        val like = LikeRecord(subject, Instant.parse("2026-10-14T09:00:00Z"))
        val json = AtJson.encodeToJsonElement(LikeRecord.serializer(), like).jsonObject
        assertEquals("app.activitytracker.feed.like", json["\$type"]?.jsonPrimitive?.content)
        assertEquals(like, AtJson.decodeFromJsonElement(LikeRecord.serializer(), json))
    }

    @Test
    fun commentRecordAudienceIsOptional() {
        val subject = StrongRef(AtUri("at://did:plc:bob/${Nsid.FEED_ACTIVITY}/3kact"), Cid("bafy3kact"))
        val public = CommentRecord(subject, "Nice!", Instant.parse("2026-10-14T09:00:00Z"))
        val publicJson = AtJson.encodeToJsonElement(CommentRecord.serializer(), public).jsonObject
        assertEquals(Nsid.FEED_COMMENT, publicJson["\$type"]?.jsonPrimitive?.content)
        assertFalse("audience" in publicJson)
        assertEquals(public, AtJson.decodeFromJsonElement(CommentRecord.serializer(), publicJson))
        val followers = public.copy(audience = Visibility.FOLLOWERS)
        val followersJson = AtJson.encodeToJsonElement(CommentRecord.serializer(), followers).jsonObject
        assertEquals(Visibility.FOLLOWERS, followersJson["audience"]?.jsonPrimitive?.content)
        assertEquals(followers, AtJson.decodeFromJsonElement(CommentRecord.serializer(), followersJson))
    }

    @Test
    fun followApprovalAndDeclineRoundTrip() {
        val bob = Did("did:plc:bob")
        val approval = FollowApprovalRecord(bob, Instant.parse("2026-10-13T09:00:00Z"))
        val approvalJson = AtJson.encodeToJsonElement(FollowApprovalRecord.serializer(), approval).jsonObject
        assertEquals(Nsid.GRAPH_FOLLOW_APPROVAL, approvalJson["\$type"]?.jsonPrimitive?.content)
        assertEquals("did:plc:bob", approvalJson["subject"]?.jsonPrimitive?.content)
        assertEquals(approval, AtJson.decodeFromJsonElement(FollowApprovalRecord.serializer(), approvalJson))
        assertEquals("did:plc:bob", FollowApprovalRecord.rkey(bob))

        val decline = FollowDeclineRecord(bob, Instant.parse("2026-10-13T09:00:00Z"))
        val declineJson = AtJson.encodeToJsonElement(FollowDeclineRecord.serializer(), decline).jsonObject
        assertEquals(Nsid.GRAPH_FOLLOW_DECLINE, declineJson["\$type"]?.jsonPrimitive?.content)
        assertEquals(decline, AtJson.decodeFromJsonElement(FollowDeclineRecord.serializer(), declineJson))
    }

    @Test
    fun profileFollowPolicyIsOptional() {
        val without = AtJson.encodeToJsonElement(ProfileRecord.serializer(), ProfileRecord(displayName = "Alice")).jsonObject
        assertFalse("followPolicy" in without)
        val approve = ProfileRecord(displayName = "Alice", followPolicy = FollowPolicy.APPROVE)
        val json = AtJson.encodeToJsonElement(ProfileRecord.serializer(), approve).jsonObject
        assertEquals("approve", json["followPolicy"]?.jsonPrimitive?.content)
        assertEquals(approve, AtJson.decodeFromJsonElement(ProfileRecord.serializer(), json))
    }

    @Test
    fun profileViewDecodesFollowRequestState() {
        val view =
            AtJson.decodeFromString(
                ProfileView.serializer(),
                """{"did":"did:plc:alice","handle":"alice.test","followPolicy":"approve",""" +
                    """"viewer":{"following":"at://did:plc:bob/app.activitytracker.graph.follow/3k","followRequested":true}}""",
            )
        assertEquals(FollowPolicy.APPROVE, view.followPolicy)
        assertEquals(true, view.viewer?.followRequested)
    }

    @Test
    fun followRequestsOutputDecodes() {
        val output =
            AtJson.decodeFromString(
                GetFollowRequests.Output.serializer(),
                """{"requests":[{"did":"did:plc:bob","handle":"bob.test"}],"cursor":"c1"}""",
            )
        assertEquals(listOf("did:plc:bob"), output.requests.map { it.did.value })
        assertEquals("c1", output.cursor)
    }

    @Test
    fun applyWritesEncodesCreateAndDelete() {
        val input =
            ApplyWrites.Input(
                repo = Did("did:plc:alice"),
                writes =
                    listOf(
                        ApplyWrites.create(
                            Nsid.GRAPH_FOLLOW_APPROVAL,
                            "did:plc:bob",
                            AtJson.encodeToJsonElement(
                                FollowApprovalRecord.serializer(),
                                FollowApprovalRecord(Did("did:plc:bob"), Instant.parse("2026-10-13T09:00:00Z")),
                            ),
                        ),
                        ApplyWrites.delete(Nsid.GRAPH_FOLLOW_APPROVAL, "did:plc:carol"),
                    ),
            )
        val writes = AtJson.encodeToJsonElement(ApplyWrites.Input.serializer(), input).jsonObject.getValue("writes").jsonArray
        assertEquals("com.atproto.repo.applyWrites#create", writes[0].jsonObject["\$type"]?.jsonPrimitive?.content)
        assertEquals("did:plc:bob", writes[0].jsonObject["rkey"]?.jsonPrimitive?.content)
        assertEquals("com.atproto.repo.applyWrites#delete", writes[1].jsonObject["\$type"]?.jsonPrimitive?.content)
        assertFalse("value" in writes[1].jsonObject)
    }

    @Test
    fun thresholdHistoryRoundTrips() {
        val training =
            TrainingDetails(
                history =
                    listOf(
                        ThresholdEntry(
                            threshold = Threshold.FUNCTIONAL_THRESHOLD_POWER,
                            value = 231,
                            setAt = Instant.parse("2026-10-01T07:00:00Z"),
                            source = ThresholdSource.DEVICE,
                            device = "Garmin Edge 840",
                        ),
                        ThresholdEntry(
                            threshold = Threshold.THRESHOLD_SPEED,
                            value = 3271,
                            setAt = Instant.parse("2026-10-02T07:00:00Z"),
                            sport = Sport.RUN,
                        ),
                    )
            )
        val json = AtJson.encodeToJsonElement(TrainingDetails.serializer(), training).jsonObject
        val (ftp, pace) = json.getValue("history").jsonArray.map { it.jsonObject }
        assertEquals("functionalThresholdPower", ftp["threshold"]?.jsonPrimitive?.content)
        assertEquals("2026-10-01T07:00:00Z", ftp["setAt"]?.jsonPrimitive?.content)
        assertEquals("device", ftp["source"]?.jsonPrimitive?.content)
        assertEquals("Garmin Edge 840", ftp["device"]?.jsonPrimitive?.content)
        assertFalse("sport" in ftp)
        assertEquals("run", pace["sport"]?.jsonPrimitive?.content)
        assertFalse("source" in pace)
        assertFalse("device" in pace)
        assertEquals(training, AtJson.decodeFromJsonElement(TrainingDetails.serializer(), json))
    }
}
