package io.github.solcott.activitytracker.data.profile

import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.DetailsVisibility
import io.github.solcott.activitytracker.lexicon.app.GetPrivateDetails
import io.github.solcott.activitytracker.lexicon.app.Location
import io.github.solcott.activitytracker.lexicon.app.PowerZoneModel
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import io.github.solcott.activitytracker.lexicon.app.PutPrivateDetails
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject

/** A fake PDS and AppView that hold Alice's profile record and private details, and log each write in order. */
class ProfileRepositoryTest {
    private val did = Did("did:plc:alice")
    private var record: ProfileRecord? = null
    private var hidden = PutPrivateDetails.Input(ProfileDetails(), DetailsVisibility())

    /** `"record"` or `"private"`, with the state written, in call order. */
    private val writes = mutableListOf<Pair<String, Any>>()

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                fun body() = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.repo.getRecord" -> {
                        val current = record
                        if (current == null) {
                            respond("""{"error":"RecordNotFound"}""", HttpStatusCode.BadRequest, json)
                        } else {
                            val value = AtJson.encodeToString(ProfileRecord.serializer(), current)
                            respond("""{"uri":"at://$did/${Nsid.ACTOR_PROFILE}/self","value":$value}""", headers = json)
                        }
                    }

                    "/xrpc/com.atproto.repo.putRecord" -> {
                        val sent = AtJson.parseToJsonElement(body()).jsonObject.getValue("record")
                        record = AtJson.decodeFromJsonElement(ProfileRecord.serializer(), sent).also { writes += "record" to it }
                        respond("""{"uri":"at://$did/x/self","cid":"bafyrec"}""", headers = json)
                    }

                    "/xrpc/com.atproto.repo.uploadBlob" -> {
                        writes += "blob" to request.headers["Content-Type"].orEmpty()
                        respond(
                            """{"blob":{"${'$'}type":"blob","ref":{"${'$'}link":"bafkavatar"},"mimeType":"image/jpeg","size":3}}""",
                            headers = json,
                        )
                    }

                    "/xrpc/${Nsid.ACTOR_GET_PROFILE}" -> {
                        respond("""{"did":"$did","handle":"alice.test"}""", headers = json)
                    }

                    "/xrpc/com.atproto.server.getServiceAuth" -> {
                        respond("""{"token":"service-jwt"}""", headers = json)
                    }

                    "/xrpc/${Nsid.ACTOR_GET_PRIVATE_DETAILS}" -> {
                        val output = GetPrivateDetails.Output(hidden.details, hidden.visibility)
                        respond(AtJson.encodeToString(GetPrivateDetails.Output.serializer(), output), headers = json)
                    }

                    "/xrpc/${Nsid.ACTOR_PUT_PRIVATE_DETAILS}" -> {
                        hidden = AtJson.decodeFromString(PutPrivateDetails.Input.serializer(), body()).also { writes += "private" to it }
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

    private suspend fun repository(): ProfileRepository {
        return ProfileRepository(signedInTestSession(http, did = did).clients, Clock.System)
    }

    private val portland = Location(locality = "Portland", country = "US")

    @Test
    fun publicDetailsGoInTheRecordAndOthersToTheAppView() = runTest {
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
        assertEquals(ProfileDetails(birthDate = "1990-04-01", weight = 61_500), hidden.details)
        assertEquals(DetailsVisibility(birthDate = Visibility.SELF, weight = Visibility.MUTUALS), hidden.visibility)
        // The location is new to the record, so the private store holds it until the record does, then drops it.
        assertEquals(listOf("private", "record", "private"), writes.map { it.first })

        val own = repo.ownProfile()
        assertEquals(ProfileDetails(location = portland, birthDate = "1990-04-01", weight = 61_500), own.details)
        assertEquals(
            DetailsVisibility(Visibility.PUBLIC, Visibility.SELF, Visibility.SELF, Visibility.MUTUALS, Visibility.SELF),
            own.visibility,
        )
    }

    @Test
    fun goingPrivateWritesThePrivateStoreBeforeClearingTheRecord() = runTest {
        record = ProfileRecord(details = ProfileDetails(weight = 61_500))
        val repo = repository()
        repo.save(ProfileEdit("", "", ProfileDetails(weight = 61_500), DetailsVisibility(weight = Visibility.MUTUALS)))

        assertEquals(listOf("private", "record"), writes.map { it.first })
        assertEquals(61_500, (writes[0].second as PutPrivateDetails.Input).details.weight)
        assertNull((writes[1].second as ProfileRecord).details)
    }

    @Test
    fun goingPublicKeepsThePrivateCopyUntilTheRecordHoldsIt() = runTest {
        hidden = PutPrivateDetails.Input(ProfileDetails(gender = "female"), DetailsVisibility(gender = Visibility.MUTUALS))
        val repo = repository()
        repo.save(ProfileEdit("", "", ProfileDetails(gender = "female"), DetailsVisibility(gender = Visibility.PUBLIC)))

        assertEquals(listOf("private", "record", "private"), writes.map { it.first })
        assertEquals("female", (writes[0].second as PutPrivateDetails.Input).details.gender)
        assertEquals("female", (writes[1].second as ProfileRecord).details?.gender)
        assertEquals(ProfileDetails(), hidden.details)
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
    fun newAvatarIsUploadedBeforeTheRecordAndShownAtOnce() = runTest {
        val repo = repository()
        repo.save(ProfileEdit("Alice", "", ProfileDetails(), DetailsVisibility(), AvatarEdit.Replace(byteArrayOf(1, 2, 3), "image/jpeg")))

        assertEquals(listOf("private", "blob", "record"), writes.map { it.first })
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
    fun trainingAndProfileEditsKeepEachOthersDetails() = runTest {
        record = ProfileRecord(displayName = "Alice", details = ProfileDetails(location = portland))
        hidden = PutPrivateDetails.Input(ProfileDetails(weight = 61_500), DetailsVisibility(weight = Visibility.MUTUALS))
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
}
