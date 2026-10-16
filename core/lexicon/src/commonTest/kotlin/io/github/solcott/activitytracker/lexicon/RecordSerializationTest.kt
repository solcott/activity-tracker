package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActivityDefaultsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveGas
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveTank
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivitySensor
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CommentHideRecord
import io.github.solcott.activitytracker.lexicon.app.CommentRecord
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.FollowApprovalRecord
import io.github.solcott.activitytracker.lexicon.app.FollowDeclineRecord
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.GetFollowRequests
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.HeartRateZoneModel
import io.github.solcott.activitytracker.lexicon.app.LikeRecord
import io.github.solcott.activitytracker.lexicon.app.Location
import io.github.solcott.activitytracker.lexicon.app.NotifySpace
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import io.github.solcott.activitytracker.lexicon.app.PrivacyZonesRecord
import io.github.solcott.activitytracker.lexicon.app.PrivateDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.ReplyRef
import io.github.solcott.activitytracker.lexicon.app.RouteTrimRecord
import io.github.solcott.activitytracker.lexicon.app.SensorType
import io.github.solcott.activitytracker.lexicon.app.SpeedZoneModel
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SportTraining
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.ThreadView
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.ThresholdEntry
import io.github.solcott.activitytracker.lexicon.app.ThresholdSource
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.TrainingSetAt
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.WaterType
import io.github.solcott.activitytracker.lexicon.atproto.ApplyWrites
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun newDiveAndTankFieldsRoundTripAndOlderDivesLackThem() {
        val dive =
            record.copy(
                sport = Sport.DIVE,
                dive =
                    ActivityDive(
                        startN2 = 12,
                        endN2 = 64,
                        maxAscentRate = 150,
                        descentTime = 180,
                        waterType = WaterType.SALT,
                        waterDensity = 1025,
                        airTemperature = -20,
                        visibility = 1500,
                        highestPpO2 = 1400,
                        tanks = listOf(ActivityDiveTank(volume = 12_000, gas = ActivityDiveGas(32, 0))),
                    ),
            )
        val json = AtJson.encodeToString(ActivityRecord.serializer(), dive)
        assertTrue(""""waterType":"salt"""" in json, json)
        assertTrue(""""tanks":[{"volume":12000,"gas":{"oxygen":32,"helium":0}}]""" in json, json)
        assertEquals(dive, AtJson.decodeFromString(ActivityRecord.serializer(), json))
        val older =
            """{"${'$'}type":"${Nsid.FEED_ACTIVITY}","sport":"dive","startedAt":"2026-09-30T14:00:00Z","elapsedTime":1800,""" +
                """"createdAt":"2026-09-30T14:31:00Z","dive":{"maxDepth":3012,"tanks":[{"startPressure":200000}]}}"""
        val decoded = AtJson.decodeFromString(ActivityRecord.serializer(), older).dive!!
        assertNull(decoded.waterType)
        assertNull(decoded.tanks!!.single().gas)
    }

    @Test
    fun summaryFieldsRoundTripAndOmitTheirNulls() {
        val ride =
            record.copy(
                sport = Sport.RIDE,
                elevationLoss = 420,
                avgCadence = 88,
                maxCadence = 120,
                avgPower = 210,
                maxPower = 850,
                normalizedPower = 232,
                work = 1512,
                calories = 1450,
                frontShifts = 12,
                rearShifts = 240,
                jumps = 3,
                grit = 125,
                flow = 42,
            )
        val json = AtJson.encodeToString(ActivityRecord.serializer(), ride)
        assertTrue(""""normalizedPower":232""" in json && """"calories":1450""" in json && """"flow":42""" in json, json)
        assertEquals(ride, AtJson.decodeFromString(ActivityRecord.serializer(), json))
        assertFalse("avgPower" in AtJson.encodeToString(ActivityRecord.serializer(), record))
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
    fun privacyZonesAndRouteTrimRoundTrip() {
        val record =
            PrivacyZonesRecord(
                revision = "3l2abcdefgh22",
                zones = listOf(PrivacyZone(latitudeE7 = 515_000_000, longitudeE7 = -1_275_000, radiusMeters = 400, label = "Home")),
                hideEndsMeters = 200,
            )
        val json = AtJson.encodeToJsonElement(PrivacyZonesRecord.serializer(), record).jsonObject
        assertEquals(Nsid.ACTOR_PRIVACY_ZONES, json["\$type"]?.jsonPrimitive?.content)
        assertEquals("515000000", json["zones"]?.jsonArray?.single()?.jsonObject?.get("latitudeE7")?.jsonPrimitive?.content)
        assertEquals(record, AtJson.decodeFromJsonElement(PrivacyZonesRecord.serializer(), json))

        val bare = PrivacyZonesRecord(revision = "3l2abcdefgh22")
        val bareJson = AtJson.encodeToJsonElement(PrivacyZonesRecord.serializer(), bare).jsonObject
        assertFalse("zones" in bareJson)
        assertFalse("hideEndsMeters" in bareJson)
        val unknown =
            AtJson.decodeFromString(
                PrivacyZonesRecord.serializer(),
                """{"${'$'}type":"${Nsid.ACTOR_PRIVACY_ZONES}","revision":"3l2abcdefgh22","later":1}""",
            )
        assertEquals(bare, unknown)

        val trim = RouteTrimRecord(revision = "3l2abcdefgh22", updatedAt = Instant.parse("2026-10-15T12:00:00Z"))
        val trimJson = AtJson.encodeToJsonElement(RouteTrimRecord.serializer(), trim).jsonObject
        assertEquals(Nsid.ACTOR_ROUTE_TRIM, trimJson["\$type"]?.jsonPrimitive?.content)
        assertEquals(trim, AtJson.decodeFromJsonElement(RouteTrimRecord.serializer(), trimJson))
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
    fun commentRecordReplyRoundTrips() {
        val subject = StrongRef(AtUri("at://did:plc:alice/${Nsid.FEED_ACTIVITY}/3kact"), Cid("bafy3kact"))
        val root = StrongRef(AtUri("at://did:plc:bob/${Nsid.FEED_COMMENT}/3kroot"), Cid("bafy3kroot"))
        val parent = StrongRef(AtUri("at://did:plc:carol/${Nsid.FEED_COMMENT}/3kparent"), Cid("bafy3kparent"))
        val reply = CommentRecord(subject, "Agreed", Instant.parse("2026-10-14T09:00:00Z"), reply = ReplyRef(root, parent))
        val json = AtJson.encodeToJsonElement(CommentRecord.serializer(), reply).jsonObject
        val replyJson = json["reply"]?.jsonObject
        assertEquals(root.uri.value, replyJson?.get("root")?.jsonObject?.get("uri")?.jsonPrimitive?.content)
        assertEquals(parent.cid.value, replyJson?.get("parent")?.jsonObject?.get("cid")?.jsonPrimitive?.content)
        assertEquals(reply, AtJson.decodeFromJsonElement(CommentRecord.serializer(), json))
    }

    @Test
    fun commentRecordWithoutReplyIsTopLevel() {
        val json =
            """{"${'$'}type":"${Nsid.FEED_COMMENT}","subject":{"uri":"at://did:plc:alice/${Nsid.FEED_ACTIVITY}/3kact","cid":"bafy"},""" +
                """"text":"Hi","createdAt":"2026-10-14T09:00:00Z"}"""
        val record = AtJson.decodeFromString(CommentRecord.serializer(), json)
        assertNull(record.reply)
        assertFalse("reply" in AtJson.encodeToJsonElement(CommentRecord.serializer(), record).jsonObject)
    }

    @Test
    fun visibilityNarrowerOverEveryPair() {
        for ((i, a) in Visibility.all.withIndex()) {
            for ((j, b) in Visibility.all.withIndex()) {
                assertEquals(Visibility.all[maxOf(i, j)], Visibility.narrower(a, b), "narrower($a, $b)")
            }
        }
    }

    @Test
    fun commentHideRecordCarriesItsTypeAndRoundTrips() {
        val hide = CommentHideRecord(AtUri("at://did:plc:bob/${Nsid.FEED_COMMENT}/3kcom"), Instant.parse("2026-10-14T09:00:00Z"))
        val json = AtJson.encodeToJsonElement(CommentHideRecord.serializer(), hide).jsonObject
        assertEquals(Nsid.FEED_COMMENT_HIDE, json["\$type"]?.jsonPrimitive?.content)
        assertEquals("at://did:plc:bob/${Nsid.FEED_COMMENT}/3kcom", json["subject"]?.jsonPrimitive?.content)
        assertEquals(hide, AtJson.decodeFromJsonElement(CommentHideRecord.serializer(), json))
        assertTrue(Nsid.FEED_COMMENT_HIDE in Nsid.indexedCollections)
    }

    @Test
    fun commentViewHiddenIsOptional() {
        val author = ProfileViewBasic(Did("did:plc:bob"), Handle("bob.test"))
        val shown =
            CommentView(
                AtUri("at://did:plc:bob/${Nsid.FEED_COMMENT}/3kcom"),
                Cid("bafy3kcom"),
                author,
                "Hi",
                Instant.parse("2026-10-14T09:00:00Z"),
            )
        val shownJson = AtJson.encodeToJsonElement(CommentView.serializer(), shown).jsonObject
        assertFalse("hidden" in shownJson)
        assertEquals(shown, AtJson.decodeFromJsonElement(CommentView.serializer(), shownJson))
        val hidden = shown.copy(hidden = true)
        val hiddenJson = AtJson.encodeToJsonElement(CommentView.serializer(), hidden).jsonObject
        assertEquals("true", hiddenJson["hidden"]?.jsonPrimitive?.content)
        assertEquals(hidden, AtJson.decodeFromJsonElement(CommentView.serializer(), hiddenJson))
    }

    @Test
    fun threadViewWithoutRootIsAPlaceholder() {
        val author = ProfileViewBasic(Did("did:plc:carol"), Handle("carol.test"))
        val root = StrongRef(AtUri("at://did:plc:bob/${Nsid.FEED_COMMENT}/3kroot"), Cid("bafy3kroot"))
        val reply =
            CommentView(
                AtUri("at://did:plc:carol/${Nsid.FEED_COMMENT}/3krep"),
                Cid("bafy3krep"),
                author,
                "Agreed",
                Instant.parse("2026-10-14T09:00:00Z"),
                audience = Visibility.FOLLOWERS,
                root = root,
                parent = root,
            )
        val thread = ThreadView(root.uri, root = null, replies = listOf(reply), replyCount = 1)
        val json = AtJson.encodeToJsonElement(ThreadView.serializer(), thread).jsonObject
        assertFalse("root" in json)
        assertFalse("repliesCursor" in json)
        assertFalse("ref" in json["replies"]!!.jsonArray[0].jsonObject)
        assertEquals(thread, AtJson.decodeFromJsonElement(ThreadView.serializer(), json))
        val output = AtJson.decodeFromString(GetComments.Output.serializer(), """{"uri":"${root.uri.value}","comments":[]}""")
        assertNull(output.threads)
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
    fun profileViewDecodesFollowedBy() {
        val view =
            AtJson.decodeFromString(
                ProfileView.serializer(),
                """{"did":"did:plc:alice","handle":"alice.test",""" +
                    """"viewer":{"followedBy":"at://did:plc:alice/app.activitytracker.graph.follow/3j"}}""",
            )
        assertEquals(AtUri("at://did:plc:alice/app.activitytracker.graph.follow/3j"), view.viewer?.followedBy)
        assertNull(view.viewer?.following)
    }

    @Test
    fun activityOutputStillWritesEmptyComments() {
        val view =
            ActivityView(
                uri = AtUri("at://did:plc:alice/${Nsid.FEED_ACTIVITY}/3k"),
                cid = Cid("bafyrei"),
                author = ProfileViewBasic(Did("did:plc:alice"), Handle("alice.test")),
                record = record,
                likeCount = 0,
                commentCount = 12,
                indexedAt = Instant.parse("2026-10-14T09:00:00Z"),
            )
        val json = AtJson.encodeToString(GetActivity.Output.serializer(), GetActivity.Output(view))
        // Clients from before getComments require the field.
        assertTrue("\"comments\":[]" in json)
        assertEquals(GetActivity.Output(view), AtJson.decodeFromString(GetActivity.Output.serializer(), json))
    }

    @Test
    fun commentsParamsRoundTripThroughQueryString() {
        val activity = AtUri("at://did:plc:alice/${Nsid.FEED_ACTIVITY}/3k")
        val first = AtUri("at://did:plc:bob/${Nsid.FEED_COMMENT}/3ka")
        val second = AtUri("at://did:plc:carol/${Nsid.FEED_COMMENT}/3kb")
        val params = GetComments.Params(activity, uris = listOf(first, second))
        val encoded = XrpcParams.encode(GetComments.Params.serializer(), params)
        assertEquals(listOf("uri" to activity.value, "uris" to first.value, "uris" to second.value), encoded)
        assertEquals(params, XrpcParams.decode(GetComments.Params.serializer(), encoded.groupBy({ it.first }, { it.second })))
        // A single URI still decodes to a list.
        val one = XrpcParams.decode(GetComments.Params.serializer(), mapOf("uri" to listOf(activity.value), "uris" to listOf(first.value)))
        assertEquals(listOf(first), one.uris)
        val page = GetComments.Params(activity, limit = 30, cursor = "1760000000000::at://x")
        assertEquals(
            page,
            XrpcParams.decode(
                GetComments.Params.serializer(),
                XrpcParams.encode(GetComments.Params.serializer(), page).groupBy({ it.first }, { it.second }),
            ),
        )
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
