package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.PdsLookup
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.index.HandleLookup
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.DetailsVisibility
import io.github.solcott.activitytracker.lexicon.app.GetPrivateDetails
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.HeartRateZoneModel
import io.github.solcott.activitytracker.lexicon.app.Location
import io.github.solcott.activitytracker.lexicon.app.PowerZoneModel
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.PutPrivateDetails
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.TrainingSetAt
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database

/** Public details come from the indexed profile record; mutuals-only and private ones from `putPrivateDetails`, shown per viewer. */
class ProfileDetailsTest {
    private val service = "did:web:localhost%3A8081"
    private val alice = "did:plc:alice"
    private val bob = "did:plc:bob"
    private val carol = "did:plc:carol"
    private val keys = mapOf(alice to TestAccountKey(), bob to TestAccountKey(), carol to TestAccountKey())

    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val indexer = Indexer(db, HandleLookup { did -> did.value.substringAfterLast(':') + ".test" })
    private val config = AppViewConfig.fromEnv(emptyMap())
    private val verifier = ServiceAuthVerifier(service, keys = { keys[it.value]?.multibase })
    private val pds = PdsLookup { did -> "http://pds.test".takeIf { did.value == alice } }

    private fun commit(did: String, collection: String, rkey: String, record: String?, op: String = "create") = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-09-30T12:00:00.000000Z",
                operation = op,
                collection = collection,
                rkey = rkey,
                record = record?.let { AtJson.parseToJsonElement(it).jsonObject },
                cid = "bafy$rkey",
            )
        )
    }

    private fun follow(author: String, subject: String) =
        commit(
            author,
            Nsid.GRAPH_FOLLOW,
            "3kf${author.last()}${subject.last()}",
            """{"subject":"$subject","createdAt":"2026-09-30T09:00:00Z"}""",
        )

    private fun auth(did: String, nsid: String) = "Bearer ${keys.getValue(did).serviceJwt(did, service, nsid)}"

    private suspend fun HttpClient.profile(viewer: String?): ProfileView {
        val nsid = GetProfile.method.nsid
        val response = get("/xrpc/$nsid?actor=$alice") { viewer?.let { header(HttpHeaders.Authorization, auth(it, nsid)) } }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return AtJson.decodeFromString(GetProfile.method.output, response.bodyAsText())
    }

    private suspend fun HttpClient.putPrivate(caller: String?, input: PutPrivateDetails.Input): HttpResponse {
        val nsid = PutPrivateDetails.method.nsid
        return post("/xrpc/$nsid") {
            caller?.let { header(HttpHeaders.Authorization, auth(it, nsid)) }
            contentType(ContentType.Application.Json)
            setBody(AtJson.encodeToString(PutPrivateDetails.Input.serializer(), input))
        }
    }

    /** Alice: public city and avatar in her record; weight for mutuals and birthdate for herself in the AppView. */
    private suspend fun ApplicationTestBuilder.seedAlice() {
        application { appView(config, db, verifier, pds) }
        commit(
            alice,
            Nsid.ACTOR_PROFILE,
            "self",
            """{"displayName":"Alice",""" +
                """"avatar":{"${'$'}type":"blob","ref":{"${'$'}link":"bafkavatar"},"mimeType":"image/jpeg","size":2048},""" +
                """"details":{"location":{"locality":"Portland","country":"US"},"weight":12}}""",
        )
        val response =
            client.putPrivate(
                alice,
                PutPrivateDetails.Input(
                    ProfileDetails(birthDate = "1990-04-01", weight = 61_500),
                    DetailsVisibility(birthDate = Visibility.SELF, weight = Visibility.MUTUALS),
                ),
            )
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
    }

    @Test
    fun eachViewerSeesOnlyWhatVisibilityAllows() = testApplication {
        seedAlice()
        // Alice's record also says weight=12, so the public copy wins over the mutuals-only one: it's already public.
        commit(
            alice,
            Nsid.ACTOR_PROFILE,
            "self",
            """{"displayName":"Alice","details":{"location":{"locality":"Portland","country":"US"}}}""",
        )
        follow(bob, alice) // Bob follows Alice, but she doesn't follow him back.
        follow(alice, carol)
        follow(carol, alice) // Carol and Alice are mutuals.

        val publicOnly = ProfileDetails(location = Location(locality = "Portland", country = "US"))
        assertEquals(publicOnly, client.profile(viewer = null).details)
        assertEquals(publicOnly, client.profile(viewer = bob).details)
        assertEquals(publicOnly.copy(weight = 61_500), client.profile(viewer = carol).details)
        assertEquals(publicOnly.copy(weight = 61_500, birthDate = "1990-04-01"), client.profile(viewer = alice).details)
    }

    @Test
    fun publicCopyWinsAndAvatarRedirectsToPds() = testApplication {
        seedAlice()
        val view = client.profile(viewer = null)
        assertEquals(12, view.details?.weight)
        assertEquals("${config.publicUrl}/img/avatar/$alice/bafkavatar", view.avatar)

        val noRedirects = createClient { followRedirects = false }
        val redirect = noRedirects.get("/img/avatar/$alice/bafkavatar")
        assertEquals(HttpStatusCode.Found, redirect.status)
        assertEquals(
            "http://pds.test/xrpc/com.atproto.sync.getBlob?did=did%3Aplc%3Aalice&cid=bafkavatar",
            redirect.headers[HttpHeaders.Location],
        )
        // Only the athlete's current avatar is served, not any blob.
        assertEquals(HttpStatusCode.NotFound, noRedirects.get("/img/avatar/$alice/bafkother").status)
    }

    @Test
    fun privateDetailsNeedAuthAndANonPublicVisibility() = testApplication {
        seedAlice()
        val input = PutPrivateDetails.Input(ProfileDetails(gender = "female"), DetailsVisibility(gender = Visibility.SELF))
        assertEquals(HttpStatusCode.Unauthorized, client.putPrivate(null, input).status)

        val public = client.putPrivate(alice, input.copy(visibility = DetailsVisibility(gender = Visibility.PUBLIC)))
        assertEquals(HttpStatusCode.BadRequest, public.status)
        assertEquals(
            PutPrivateDetails.INVALID_VISIBILITY,
            AtJson.parseToJsonElement(public.bodyAsText()).jsonObject["error"]?.toString()?.trim('"'),
        )

        val badDate =
            client.putPrivate(
                alice,
                PutPrivateDetails.Input(ProfileDetails(birthDate = "1990-13-45"), DetailsVisibility(birthDate = Visibility.SELF)),
            )
        assertEquals(HttpStatusCode.BadRequest, badDate.status)

        // Each caller reads only their own private details.
        val nsid = GetPrivateDetails.method.nsid
        assertEquals(HttpStatusCode.Unauthorized, client.get("/xrpc/$nsid").status)
        suspend fun own(did: String) =
            AtJson.decodeFromString(
                GetPrivateDetails.Output.serializer(),
                client.get("/xrpc/$nsid") { header(HttpHeaders.Authorization, auth(did, nsid)) }.bodyAsText(),
            )
        assertEquals(ProfileDetails(birthDate = "1990-04-01", weight = 61_500), own(alice).details)
        assertEquals(Visibility.MUTUALS, own(alice).visibility.weight)
        assertEquals(ProfileDetails(), own(bob).details)
    }

    @Test
    fun trainingIsStoredAndShownByVisibility() = testApplication {
        seedAlice()
        val training =
            TrainingDetails(
                functionalThresholdPower = 231,
                thresholdHeartRate = 168,
                heartRateZoneModel = HeartRateZoneModel.FRIEL_CYCLING,
            )
        val tooFast =
            client.putPrivate(
                alice,
                PutPrivateDetails.Input(
                    ProfileDetails(training = training.copy(maxHeartRate = 400)),
                    DetailsVisibility(training = Visibility.SELF),
                ),
            )
        assertEquals(HttpStatusCode.BadRequest, tooFast.status)
        val saved =
            client.putPrivate(
                alice,
                PutPrivateDetails.Input(ProfileDetails(training = training), DetailsVisibility(training = Visibility.MUTUALS)),
            )
        assertEquals(HttpStatusCode.OK, saved.status, saved.bodyAsText())
        follow(alice, carol)
        follow(carol, alice)
        assertEquals(training, client.profile(viewer = carol).details?.training)
        assertNull(client.profile(viewer = bob).details?.training)

        // Auto-update choices and when each value was set round-trip; a repeated choice is dropped from a record.
        val auto =
            training.copy(
                autoUpdate = listOf(Threshold.MAX_HEART_RATE, Threshold.FUNCTIONAL_THRESHOLD_POWER),
                setAt = TrainingSetAt(maxHeartRate = Instant.parse("2026-10-01T07:00:00Z")),
            )
        val withAuto =
            client.putPrivate(
                alice,
                PutPrivateDetails.Input(ProfileDetails(training = auto), DetailsVisibility(training = Visibility.SELF)),
            )
        assertEquals(HttpStatusCode.OK, withAuto.status, withAuto.bodyAsText())
        assertEquals(auto, client.profile(viewer = alice).details?.training)
        val repeated =
            client.putPrivate(
                alice,
                PutPrivateDetails.Input(
                    ProfileDetails(training = auto.copy(autoUpdate = listOf(Threshold.MAX_HEART_RATE, Threshold.MAX_HEART_RATE))),
                    DetailsVisibility(training = Visibility.SELF),
                ),
            )
        assertEquals(HttpStatusCode.BadRequest, repeated.status)

        // A public copy in the record is indexed and shown to everyone; an out-of-range value in it is dropped.
        commit(
            alice,
            Nsid.ACTOR_PROFILE,
            "self",
            """{"details":{"training":{"functionalThresholdPower":250,"maxHeartRate":999,"powerZoneModel":"coggan7"}}}""",
        )
        assertEquals(
            TrainingDetails(functionalThresholdPower = 250, powerZoneModel = PowerZoneModel.COGGAN_7),
            client.profile(viewer = null).details?.training,
        )
    }

    @Test
    fun deletedAccountLosesItsPrivateDetails() = testApplication {
        seedAlice()
        runBlocking {
            indexer.handle(
                JetstreamEvent.Account(
                    did = alice,
                    time = "2026-09-30T12:00:00.000000Z",
                    account = JetstreamEvent.Account.Body(did = alice, active = false, status = "deleted"),
                )
            )
        }
        val nsid = GetPrivateDetails.method.nsid
        val own = client.get("/xrpc/$nsid") { header(HttpHeaders.Authorization, auth(alice, nsid)) }
        assertNull(AtJson.decodeFromString(GetPrivateDetails.Output.serializer(), own.bodyAsText()).details.weight)
    }
}
