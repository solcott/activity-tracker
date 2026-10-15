package io.github.solcott.activitytracker.data.profile

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.activityfile.DeviceThresholds
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.training.ThresholdChange
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.Location
import io.github.solcott.activitytracker.lexicon.app.PowerZoneModel
import io.github.solcott.activitytracker.lexicon.app.PrivateDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SportTraining
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.ThresholdEntry
import io.github.solcott.activitytracker.lexicon.app.ThresholdSource
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.TrainingSetAt
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A fake PDS holding Alice's profile record and her tier spaces' `privateDetails` records, logging each write in order, and an AppView
 * serving public profiles. Bob is on a PDS of his own, where Alice reads his Mutuals tier as a member.
 */
class ProfileRepositoryTest {
    private val did = Did("did:plc:alice")
    private val bob = Did("did:plc:bob")
    private var record: ProfileRecord? = null

    /** Alice's tier spaces: each tier's details, or none. */
    private val tiers = mutableMapOf<String, ProfileDetails>()

    /** Alice's non-public details as one value, split into her tiers by visibility (Only me when unset). */
    private data class Hidden(val details: ProfileDetails, val visibility: DetailsVisibility)

    private var hidden: Hidden
        get() {
            var details = ProfileDetails()
            var visibility = DetailsVisibility()
            for ((tier, held) in tiers) {
                for (group in DetailGroup.entries.filter { it.isSet(held) }) {
                    details = group.copy(held, details)
                    visibility = group.withVisibility(visibility, tier)
                }
            }
            return Hidden(details, visibility)
        }
        set(value) {
            tiers.clear()
            for (group in DetailGroup.entries.filter { it.isSet(value.details) }) {
                val tier = group.visibility(value.visibility) ?: Visibility.SELF
                tiers[tier] = group.copy(value.details, tiers[tier] ?: ProfileDetails())
            }
        }

    /** `"record"`, `"blob"`, `"put <tier>"` or `"delete <tier>"`, with what was written, in call order. */
    private val writes = mutableListOf<Pair<String, Any>>()

    /** Fail the first space write after this many have succeeded; null never fails. */
    private var failSpaceWritesAfter: Int? = null
    private var scopeMissing = false

    /** Bob's Mutuals details on his PDS, and how his PDS answers Alice. */
    private var bobMutuals: ProfileDetails? = ProfileDetails(weight = 80_000)
    private var bobFollowers: ProfileDetails? = null
    private var aliceRequestedBob = false
    private var bobRefusesAlice = false
    private var bobPdsDown = false
    private var aliceFollowsBob = true
    private val credentialRequests = mutableListOf<String>()

    /** The tier of each `createSpace` call, in order. */
    private val spacesEnsured = mutableListOf<String>()

    /** The tier of each `notifySpace` the AppView got, in order. */
    private val spacesAnnounced = mutableListOf<String>()

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                fun body() = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                fun tierOf(space: String?) = space.orEmpty().substringAfterLast('/')
                fun error(name: String, status: HttpStatusCode = HttpStatusCode.BadRequest) = respond("""{"error":"$name"}""", status, json)
                val path = request.url.encodedPath
                when ("${request.url.host}$path") {
                    "pds.test/xrpc/com.atproto.repo.getRecord" -> {
                        val current = record
                        if (current == null) {
                            error("RecordNotFound")
                        } else {
                            val value = AtJson.encodeToString(ProfileRecord.serializer(), current)
                            respond("""{"uri":"at://$did/${Nsid.ACTOR_PROFILE}/self","value":$value}""", headers = json)
                        }
                    }

                    "pds.test/xrpc/com.atproto.repo.putRecord" -> {
                        val sent = AtJson.parseToJsonElement(body()).jsonObject.getValue("record")
                        record = AtJson.decodeFromJsonElement(ProfileRecord.serializer(), sent).also { writes += "record" to it }
                        respond("""{"uri":"at://$did/x/self","cid":"bafyrec"}""", headers = json)
                    }

                    "pds.test/xrpc/com.atproto.repo.uploadBlob" -> {
                        writes += "blob" to request.headers["Content-Type"].orEmpty()
                        respond(
                            """{"blob":{"${'$'}type":"blob","ref":{"${'$'}link":"bafkavatar"},"mimeType":"image/jpeg","size":3}}""",
                            headers = json,
                        )
                    }

                    "appview.test/.well-known/did.json" -> {
                        respond(
                            """{"id":"did:web:appview.test","service":[
                               {"id":"#activitytracker_space","type":"AtprotoSpaceService","serviceEndpoint":"http://appview.test"}]}""",
                            headers = json,
                        )
                    }

                    "pds.test/xrpc/com.atproto.simplespace.createSpace" -> {
                        val tier = AtJson.parseToJsonElement(body()).jsonObject.getValue("skey").jsonPrimitive.content
                        spacesEnsured += tier
                        respond("""{"uri":"at://$did/space/${Nsid.SPACE_ATHLETE}/$tier"}""", headers = json)
                    }

                    "pds.test/xrpc/com.atproto.space.getRecord" -> {
                        val held = tiers[tierOf(request.url.parameters["space"])]
                        when {
                            scopeMissing -> error(SpaceErrors.SCOPE_MISSING, HttpStatusCode.Forbidden)
                            held == null -> error("RecordNotFound")
                            else -> respondTier(held)
                        }
                    }

                    "pds.test/xrpc/com.atproto.space.putRecord",
                    "pds.test/xrpc/com.atproto.space.deleteRecord" -> {
                        val spaceWrites = writes.count { it.first.startsWith("put") || it.first.startsWith("delete") }
                        if (scopeMissing) return@MockEngine error(SpaceErrors.SCOPE_MISSING, HttpStatusCode.Forbidden)
                        if (failSpaceWritesAfter?.let { spaceWrites >= it } == true) {
                            return@MockEngine error("InternalServerError", HttpStatusCode.InternalServerError)
                        }
                        val input = AtJson.parseToJsonElement(body()).jsonObject
                        val tier = tierOf(input.getValue("space").jsonPrimitive.content)
                        val sent = input["record"]
                        if (sent == null) {
                            tiers.remove(tier)
                            writes += "delete $tier" to tier
                        } else {
                            val details = AtJson.decodeFromJsonElement(PrivateDetailsRecord.serializer(), sent).details
                            tiers[tier] = details
                            writes += "put $tier" to details
                        }
                        respond("""{"uri":"at://$did/${Nsid.ACTOR_PRIVATE_DETAILS}/self","cid":"bafyrec"}""", headers = json)
                    }

                    "appview.test/xrpc/${Nsid.SPACE_NOTIFY_SPACE}" -> {
                        spacesAnnounced += tierOf(AtJson.parseToJsonElement(body()).jsonObject.getValue("space").jsonPrimitive.content)
                        respond("{}", headers = json)
                    }

                    "pds.test/xrpc/com.atproto.server.getServiceAuth" -> {
                        respond("""{"token":"service-jwt"}""", headers = json)
                    }

                    "pds.test/xrpc/com.atproto.space.getDelegationToken" -> {
                        credentialRequests += request.url.parameters["space"].orEmpty()
                        respond("""{"token":"delegation"}""", headers = json)
                    }

                    "plc.test/${bob.value}" -> {
                        respond(
                            """{"id":"${bob.value}","alsoKnownAs":["at://bob.test"],"service":[
                               {"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"http://bobpds.test"}]}""",
                            headers = json,
                        )
                    }

                    "bobpds.test/xrpc/com.atproto.space.getSpaceCredential" -> {
                        if (bobRefusesAlice) error(SpaceErrors.USER_NOT_AUTHORIZED)
                        else respond("""{"credential":"$credential"}""", headers = json)
                    }

                    "bobpds.test/xrpc/com.atproto.space.getRecord" -> {
                        val held = if (tierOf(request.url.parameters["space"]) == AthleteSpace.FOLLOWERS) bobFollowers else bobMutuals
                        when {
                            bobPdsDown -> error("InternalServerError", HttpStatusCode.InternalServerError)
                            held == null -> error("RecordNotFound")
                            else -> respondTier(held)
                        }
                    }

                    "appview.test/xrpc/${Nsid.ACTOR_GET_PROFILE}" -> {
                        val actor = request.url.parameters["actor"]
                        if (actor == bob.value) {
                            val bobDetails = """"details":{"location":{"country":"CA"},"weight":75000}"""
                            val requested = if (aliceRequestedBob) ""","followRequested":true""" else ""
                            val viewer =
                                if (aliceFollowsBob) ""","viewer":{"following":"at://$did/${Nsid.GRAPH_FOLLOW}/3k"$requested}""" else ""
                            respond(
                                """{"did":"${bob.value}","handle":"bob.test",$bobDetails$viewer}""",
                                headers = json,
                            )
                        } else {
                            respond("""{"did":"$did","handle":"alice.test"}""", headers = json)
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

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondTier(details: ProfileDetails) =
        respond(
            """{"uri":"at://x/${Nsid.ACTOR_PRIVATE_DETAILS}/self","cid":"bafyrec","value":${
                AtJson.encodeToString(PrivateDetailsRecord.serializer(), PrivateDetailsRecord(details))
            }}""",
            headers = headersOf("Content-Type", "application/json"),
        )

    private suspend fun repository(clock: Clock = Clock.System): ProfileRepository {
        return ProfileRepository(signedInTestSession(http, did = did).clients, clock, Logger, TrainingNarrowing {})
    }

    private val portland = Location(locality = "Portland", country = "US")

    @Test
    fun savingATierAnnouncesItsSpaceToTheAppView() = runTest {
        record = ProfileRecord()
        val repo = repository()
        val edit =
            ProfileEdit(
                displayName = "Alice",
                description = "",
                details = ProfileDetails(weight = 61_500, birthDate = "1990-04-01"),
                visibility = DetailsVisibility(Visibility.PUBLIC, Visibility.SELF, Visibility.SELF, Visibility.MUTUALS),
            )
        repo.save(edit)
        assertEquals(spacesEnsured.sorted(), spacesAnnounced.sorted())
        assertTrue(Visibility.SELF in spacesAnnounced && Visibility.MUTUALS in spacesAnnounced, "$spacesAnnounced")
        // Once per session: a second save doesn't announce again.
        repo.save(edit)
        assertEquals(spacesEnsured.size, spacesAnnounced.size)
    }

    @Test
    fun eachDetailGoesInTheRecordOrItsTierSpace() = runTest {
        record = ProfileRecord(units = "imperial")
        val repo = repository()
        repo.save(
            ProfileEdit(
                displayName = "Alice",
                description = "",
                details = ProfileDetails(location = portland, weight = 61_500, birthDate = "1990-04-01"),
                visibility = DetailsVisibility(Visibility.PUBLIC, Visibility.SELF, Visibility.SELF, Visibility.MUTUALS),
            )
        )

        val saved = record!!
        assertEquals(ProfileDetails(location = portland), saved.details)
        assertEquals("imperial", saved.units)
        assertEquals(ProfileDetails(weight = 61_500), tiers[AthleteSpace.MUTUALS])
        assertEquals(ProfileDetails(birthDate = "1990-04-01"), tiers[AthleteSpace.SELF])
        assertEquals(listOf("put mutuals", "put self", "record"), writes.map { it.first })

        val own = repo.ownProfile()
        assertEquals(ProfileDetails(location = portland, birthDate = "1990-04-01", weight = 61_500), own.details)
        assertEquals(
            DetailsVisibility(Visibility.PUBLIC, Visibility.SELF, Visibility.SELF, Visibility.MUTUALS, Visibility.SELF),
            own.visibility,
        )
        assertFalse(own.needsSignIn)
    }

    @Test
    fun aPublicOnlySaveTouchesNoSpace() = runTest {
        repository().save(ProfileEdit("", "", ProfileDetails(location = portland), DetailsVisibility(location = Visibility.PUBLIC)))
        assertEquals(listOf("record"), writes.map { it.first })
        assertTrue(tiers.isEmpty())
    }

    @Test
    fun anUnchangedSaveStillEnsuresTheTiersHoldingDetails() = runTest {
        record = ProfileRecord()
        hidden = Hidden(ProfileDetails(weight = 61_500), DetailsVisibility(weight = Visibility.MUTUALS))
        repository().save(ProfileEdit("", "", ProfileDetails(weight = 61_500), DetailsVisibility(weight = Visibility.MUTUALS)))

        assertEquals(listOf(AthleteSpace.MUTUALS), spacesEnsured)
        assertEquals(listOf("record"), writes.map { it.first })
    }

    @Test
    fun heightGoesWhereWeightGoes() = runTest {
        record = ProfileRecord()
        val repo = repository()
        val body = ProfileDetails(weight = 61_500, height = 1_680)
        repo.save(ProfileEdit("", "", body, DetailsVisibility(weight = Visibility.MUTUALS)))
        assertEquals(body, tiers[AthleteSpace.MUTUALS])
        assertEquals(null, record!!.details)

        // Height alone is still the body group, so it can be made public with no weight; the emptied tier's record goes.
        repo.save(ProfileEdit("", "", ProfileDetails(height = 1_680), DetailsVisibility(weight = Visibility.PUBLIC)))
        assertEquals(ProfileDetails(height = 1_680), record!!.details)
        assertTrue(tiers.isEmpty())
    }

    @Test
    fun goingPrivateWritesTheTierBeforeClearingTheRecord() = runTest {
        record = ProfileRecord(details = ProfileDetails(weight = 61_500))
        val repo = repository()
        repo.save(ProfileEdit("", "", ProfileDetails(weight = 61_500), DetailsVisibility(weight = Visibility.MUTUALS)))

        assertEquals(listOf("put mutuals", "record"), writes.map { it.first })
        assertEquals(61_500, (writes[0].second as ProfileDetails).weight)
        assertNull((writes[1].second as ProfileRecord).details)
    }

    @Test
    fun goingPublicKeepsTheTierCopyUntilTheRecordHoldsIt() = runTest {
        hidden = Hidden(ProfileDetails(gender = "female"), DetailsVisibility(gender = Visibility.MUTUALS))
        val repo = repository()
        repo.save(ProfileEdit("", "", ProfileDetails(gender = "female"), DetailsVisibility(gender = Visibility.PUBLIC)))

        assertEquals(listOf("record", "delete mutuals"), writes.map { it.first })
        assertEquals("female", (writes[0].second as ProfileRecord).details?.gender)
        assertTrue(tiers.isEmpty())
    }

    @Test
    fun movingBetweenTiersWritesTheNewTierFirstAndAFailureLosesNothing() = runTest {
        hidden =
            Hidden(
                ProfileDetails(weight = 61_500, birthDate = "1990-04-01"),
                DetailsVisibility(weight = Visibility.SELF, birthDate = Visibility.MUTUALS),
            )
        val repo = repository()
        // Weight Only me → Mutuals, birthdate Mutuals → Only me: no order of two plain puts is safe, so both copies are kept first.
        failSpaceWritesAfter = 2
        assertFailsWith<Exception> {
            repo.save(
                ProfileEdit(
                    "",
                    "",
                    ProfileDetails(weight = 61_500, birthDate = "1990-04-01"),
                    DetailsVisibility(weight = Visibility.MUTUALS, birthDate = Visibility.SELF),
                )
            )
        }
        assertEquals(ProfileDetails(weight = 61_500, birthDate = "1990-04-01"), tiers[AthleteSpace.MUTUALS])
        assertEquals(ProfileDetails(weight = 61_500, birthDate = "1990-04-01"), tiers[AthleteSpace.SELF])

        failSpaceWritesAfter = null
        repo.save(
            ProfileEdit(
                "",
                "",
                ProfileDetails(weight = 61_500, birthDate = "1990-04-01"),
                DetailsVisibility(weight = Visibility.MUTUALS, birthDate = Visibility.SELF),
            )
        )
        assertEquals(ProfileDetails(weight = 61_500), tiers[AthleteSpace.MUTUALS])
        assertEquals(ProfileDetails(birthDate = "1990-04-01"), tiers[AthleteSpace.SELF])
    }

    @Test
    fun aFailedTrimLeavesAPublicBoundDetailInBothPlaces() = runTest {
        hidden = Hidden(ProfileDetails(location = portland), DetailsVisibility(location = Visibility.MUTUALS))
        val repo = repository()
        record = ProfileRecord()
        failSpaceWritesAfter = 0
        // Nothing goes to a tier first: the location stays in Mutuals until the record holds it, and here the trim after that fails.
        assertFailsWith<Exception> {
            repo.save(ProfileEdit("", "", ProfileDetails(location = portland), DetailsVisibility(location = Visibility.PUBLIC)))
        }
        assertEquals(portland, record?.details?.location)
        assertEquals(ProfileDetails(location = portland), tiers[AthleteSpace.MUTUALS])
    }

    @Test
    fun unsetDetailsDefaultToOnlyMe() = runTest {
        val own = repository().ownProfile()
        assertEquals(ProfileDetails(), own.details)
        assertEquals(
            DetailsVisibility(Visibility.SELF, Visibility.SELF, Visibility.SELF, Visibility.SELF, Visibility.SELF),
            own.visibility,
        )
    }

    @Test
    fun aSessionWithoutSpaceAccessLoadsPublicDetailsAndAsksToSignIn() = runTest {
        record = ProfileRecord(details = ProfileDetails(location = portland))
        scopeMissing = true
        val own = repository().ownProfile()
        assertTrue(own.needsSignIn)
        assertEquals(ProfileDetails(location = portland), own.details)
    }

    @Test
    fun newAvatarIsUploadedBeforeTheRecordAndShownAtOnce() = runTest {
        val repo = repository()
        repo.save(ProfileEdit("Alice", "", ProfileDetails(), DetailsVisibility(), AvatarEdit.Replace(byteArrayOf(1, 2, 3), "image/jpeg")))

        assertEquals(listOf("blob", "record"), writes.map { it.first })
        assertEquals("bafkavatar", record?.avatar?.ref?.link)
        // The AppView hasn't indexed it yet, so the profile shows the blob straight from the PDS.
        assertEquals(
            "http://pds.test/xrpc/com.atproto.sync.getBlob?did=did%3Aplc%3Aalice&cid=bafkavatar",
            repo.profile(did.value).avatar,
        )

        repo.save(ProfileEdit("Alice", "", ProfileDetails(), DetailsVisibility(), AvatarEdit.Remove))
        assertNull(record?.avatar)
    }

    @Test
    fun ownProfileShowsEveryTier() = runTest {
        hidden =
            Hidden(
                ProfileDetails(weight = 61_500, birthDate = "1990-04-01", gender = "female"),
                DetailsVisibility(weight = Visibility.MUTUALS, birthDate = Visibility.SELF, gender = Visibility.FOLLOWERS),
            )
        assertEquals(
            ProfileDetails(weight = 61_500, birthDate = "1990-04-01", gender = "female"),
            repository().profile(did.value).details,
        )
    }

    @Test
    fun aMutualSeesMutualsDetailsUnderThePublicOnes() = runTest {
        bobMutuals = ProfileDetails(weight = 80_000, birthDate = "1985-02-03")
        val view: ProfileView = repository().profile(bob.value)
        // The public weight wins over the tier's.
        assertEquals(ProfileDetails(location = Location(country = "CA"), weight = 75_000, birthDate = "1985-02-03"), view.details)
        // One credential per tier space a follower may read: followers and mutuals.
        assertEquals(2, credentialRequests.size)
    }

    @Test
    fun anApprovedFollowerSeesFollowersDetails() = runTest {
        bobMutuals = null
        bobFollowers = ProfileDetails(gender = "male")
        val view = repository().profile(bob.value)
        assertEquals(ProfileDetails(location = Location(country = "CA"), gender = "male", weight = 75_000), view.details)
    }

    @Test
    fun aPendingFollowerAsksForNoCredential() = runTest {
        aliceRequestedBob = true
        bobFollowers = ProfileDetails(gender = "male")
        val view = repository().profile(bob.value)
        assertEquals(ProfileDetails(location = Location(country = "CA"), weight = 75_000), view.details)
        assertTrue(credentialRequests.isEmpty())
    }

    @Test
    fun aFollowersDetailGoesInTheFollowersSpaceOnly() = runTest {
        record = ProfileRecord()
        val repo = repository()
        repo.save(ProfileEdit("", "", ProfileDetails(gender = "female"), DetailsVisibility(gender = Visibility.FOLLOWERS)))
        assertEquals(mapOf(AthleteSpace.FOLLOWERS to ProfileDetails(gender = "female")), tiers.toMap())
        assertNull(record!!.details)
        assertEquals(Visibility.FOLLOWERS, repo.ownProfile().visibility.gender)
    }

    @Test
    fun aRefusedFollowerSeesOnlyPublicDetails() = runTest {
        bobRefusesAlice = true
        val view = repository().profile(bob.value)
        assertEquals(ProfileDetails(location = Location(country = "CA"), weight = 75_000), view.details)
    }

    @Test
    fun aNonFollowerAsksForNoCredential() = runTest {
        aliceFollowsBob = false
        val view = repository().profile(bob.value)
        assertEquals(ProfileDetails(location = Location(country = "CA"), weight = 75_000), view.details)
        assertTrue(credentialRequests.isEmpty())
    }

    @Test
    fun aFailedSpaceReadStillShowsTheProfile() = runTest {
        bobPdsDown = true
        val view = repository().profile(bob.value)
        assertEquals(ProfileDetails(location = Location(country = "CA"), weight = 75_000), view.details)
    }

    @Test
    fun trainingAndProfileEditsKeepEachOthersDetails() = runTest {
        record = ProfileRecord(displayName = "Alice", details = ProfileDetails(location = portland))
        hidden = Hidden(ProfileDetails(weight = 61_500), DetailsVisibility(weight = Visibility.MUTUALS))
        val repo = repository()
        val training = TrainingDetails(functionalThresholdPower = 231, powerZoneModel = PowerZoneModel.COGGAN_6)

        repo.saveTraining(training, Visibility.SELF)
        assertEquals(ProfileDetails(weight = 61_500, training = training), hidden.details)
        assertEquals(DetailsVisibility(weight = Visibility.MUTUALS, training = Visibility.SELF), hidden.visibility)
        assertEquals(ProfileRecord(displayName = "Alice", details = ProfileDetails(location = portland)), record)

        // The edit-profile screen doesn't send training, and saving it keeps the thresholds.
        repo.save(ProfileEdit("Alice", "", ProfileDetails(gender = "female"), DetailsVisibility(gender = Visibility.SELF)))
        assertEquals(ProfileDetails(gender = "female", training = training), hidden.details)
        assertNull(record?.details)

        // Made public, training moves into the record.
        repo.saveTraining(training, Visibility.PUBLIC)
        assertEquals(training, record?.details?.training)
        assertEquals(ProfileDetails(gender = "female"), hidden.details)
    }

    @Test
    fun deviceThresholdsUpdateOnlyChosenValuesAndKeepTheVisibility() = runTest {
        val setOn = Instant.parse("2026-09-01T00:00:00Z")
        val start = Instant.parse("2026-10-02T07:00:00Z")
        val training =
            TrainingDetails(
                functionalThresholdPower = 231,
                maxHeartRate = 190,
                autoUpdate = listOf(Threshold.FUNCTIONAL_THRESHOLD_POWER),
                setAt = TrainingSetAt(functionalThresholdPower = setOn),
            )
        hidden = Hidden(ProfileDetails(training = training), DetailsVisibility(training = Visibility.MUTUALS))
        val repo = repository()
        val device = DeviceThresholds(maxHeartRate = 185, functionalThresholdPower = 240)

        val changes = repo.applyDeviceThresholds(device, start, deviceName = "Edge 840")
        assertEquals(listOf(ThresholdChange(Threshold.FUNCTIONAL_THRESHOLD_POWER, 231, 240)), changes)
        // The values saved before history was kept join it (max heart rate undated), then the device's.
        assertEquals(
            training.copy(
                functionalThresholdPower = 240,
                setAt = TrainingSetAt(functionalThresholdPower = start),
                history =
                    listOf(
                        ThresholdEntry(Threshold.MAX_HEART_RATE, 190),
                        ThresholdEntry(Threshold.FUNCTIONAL_THRESHOLD_POWER, 231, setOn),
                        ThresholdEntry(
                            Threshold.FUNCTIONAL_THRESHOLD_POWER,
                            240,
                            start,
                            source = ThresholdSource.DEVICE,
                            device = "Edge 840",
                        ),
                    ),
            ),
            hidden.details.training,
        )
        assertEquals(Visibility.MUTUALS, hidden.visibility.training)

        writes.clear()
        assertEquals(emptyList(), repo.applyDeviceThresholds(device, start))
        assertEquals(emptyList(), writes, "nothing to change writes nothing")
    }

    @Test
    fun editsAddToTheHistoryAndRemovingTheCurrentValueRevertsIt() = runTest {
        hidden = Hidden(ProfileDetails(), DetailsVisibility())
        val now = Instant.parse("2026-10-03T07:00:00Z")
        val repo =
            repository(
                object : Clock {
                    override fun now() = now
                }
            )

        repo.updateTraining(Visibility.SELF) { it.copy(maxHeartRate = 190) }
        repo.updateTraining { it.copy(maxHeartRate = 192) }
        val history = hidden.details.training?.history.orEmpty()
        assertEquals(listOf(190L, 192L), history.map { it.value })
        assertEquals(setOf(ThresholdSource.MANUAL), history.map { it.source }.toSet())
        assertEquals(setOf(now), history.map { it.setAt }.toSet())

        repo.removeThresholdEntry(history.last())
        assertEquals(190, hidden.details.training?.maxHeartRate)
        repo.removeThresholdEntry(history.first())
        assertNull(hidden.details.training)
    }

    @Test
    fun anEditorThatRebuildsTheTrainingKeepsTheHistory() = runTest {
        val rideSet = Instant.parse("2026-09-15T07:00:00Z")
        val ride = ThresholdEntry(Threshold.FUNCTIONAL_THRESHOLD_POWER, 245, rideSet, Sport.RIDE, ThresholdSource.MANUAL)
        val olderRide = ride.copy(value = 240, setAt = Instant.parse("2026-09-01T07:00:00Z"))
        // FTP 234 was saved before set times were kept, so it has no date.
        val training =
            TrainingDetails(
                functionalThresholdPower = 234,
                sports = listOf(SportTraining(Sport.RIDE, functionalThresholdPower = 245)),
                history = listOf(olderRide, ride),
            )
        hidden = Hidden(ProfileDetails(training = training), DetailsVisibility(training = Visibility.SELF))
        val now = Instant.parse("2026-10-07T23:00:00Z")
        val repo =
            repository(
                object : Clock {
                    override fun now() = now
                }
            )

        // As the defaults editor saves: the training from its form, with only the sports carried over.
        repo.updateTraining { current -> TrainingDetails(functionalThresholdPower = 231, sports = current.sports) }

        assertEquals(
            listOf(
                ThresholdEntry(Threshold.FUNCTIONAL_THRESHOLD_POWER, 234),
                olderRide,
                ride,
                ThresholdEntry(Threshold.FUNCTIONAL_THRESHOLD_POWER, 231, now, source = ThresholdSource.MANUAL),
            ),
            hidden.details.training?.history,
        )
    }

    private companion object {
        /** A space credential the client accepts: only `exp` is read, and this one is far off. */
        val credential: String =
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).let { b64 ->
                val header = b64.encode("""{"alg":"ES256K","typ":"atproto-space-credential+jwt"}""".encodeToByteArray())
                val payload = b64.encode("""{"exp":${Instant.parse("2100-01-01T00:00:00Z").epochSeconds}}""".encodeToByteArray())
                "$header.$payload.c2ln"
            }
    }
}
