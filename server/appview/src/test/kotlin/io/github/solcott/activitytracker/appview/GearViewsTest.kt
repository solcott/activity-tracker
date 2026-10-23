package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.PdsLookup
import io.github.solcott.activitytracker.appview.api.PrivateMediaTokens
import io.github.solcott.activitytracker.appview.api.SpaceBlobs
import io.github.solcott.activitytracker.appview.db.ActivityGear
import io.github.solcott.activitytracker.atproto.xrpc.XrpcBytes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Who sees an activity's gear (`gear/activity-gear`: "Viewers see only gear they may see", "Gear shown on the activity page"). */
class GearViewsTest : GearTestBase() {
    private val photoCid = "bafkreihwzzyxpay7qndclffr6usq5te3of4fra7yucklue2b5ckxhq7aeq"
    private val photoBytes = byteArrayOf(1, 2, 3, 4)
    private val spaceBlobs = SpaceBlobs { _, repo, cid ->
        if (repo.value == alice && cid == photoCid) XrpcBytes(photoBytes, "image/jpeg") else null
    }
    private val photo =
        """"photo":{"${'$'}type":"${Nsid.FEED_ACTIVITY}#image","image":{"${'$'}type":"blob","ref":{"${'$'}link":"$photoCid"},""" +
            """"mimeType":"image/jpeg","size":4}},"""

    private fun ApplicationTestBuilder.start() {
        application {
            appView(
                config.copy(signingKey = TEST_SIGNING_KEY),
                db,
                verifier,
                PdsLookup { "http://pds.test" },
                spaceBlobs = spaceBlobs,
                mediaTokens = PrivateMediaTokens(TEST_SIGNING_KEY, MutableClock(Instant.parse("2026-10-15T12:00:01Z"))),
            )
        }
        // Carol follows Alice; Dave is a stranger.
        follow(carol, alice)
    }

    private suspend fun HttpClient.view(viewer: String?): ActivityView {
        val response =
            get("/xrpc/${GetActivity.method.nsid}?uri=${rideUri.encodeURLParameter()}") {
                viewer?.let {
                    header(HttpHeaders.Authorization, "Bearer ${keys.getValue(it).serviceJwt(it, service, GetActivity.method.nsid)}")
                }
            }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return AtJson.decodeFromString(GetActivity.method.output, response.bodyAsText()).activity
    }

    @Test
    fun aStrangerSeesNoGearOnAPublicRideWhoseGearIsOnlyMe() = testApplication {
        start()
        item("3tarmac", Visibility.PUBLIC, "Tarmac")
        ride()
        gearDetails(Visibility.SELF, listOf(gearUri("3tarmac")))

        for (viewer in listOf(dave, carol, null)) {
            val view = client.view(viewer)
            assertNull(view.gear, viewer)
            assertNull(view.effectiveRecord().gear, viewer)
        }
        assertEquals(listOf("Tarmac"), client.view(alice).gear?.map { it.name })
    }

    @Test
    fun aFollowerSeesThePublicBikeAndNothingOfThePrivatePowerMeter() = testApplication {
        start()
        item("3bike", Visibility.PUBLIC, "Stumpjumper", extra = """"kind":"mountain",""")
        item("3meter", Visibility.SELF, "Power meter", type = "sensor")
        ride(gear = listOf(gearUri("3bike"), gearUri("3meter")))

        val follower = client.view(carol)
        assertEquals(listOf(gearUri("3bike")), follower.gear?.map { it.uri.value })
        assertEquals("mountain", follower.gear?.single()?.kind)
        assertEquals(listOf(AtUri(gearUri("3bike"))), follower.record.gear, "the power meter's URI isn't served")

        val own = client.view(alice)
        assertEquals(listOf("Stumpjumper", "Power meter"), own.gear?.map { it.name })
        assertEquals(listOf(gearUri("3bike"), gearUri("3meter")), own.record.gear?.map { it.value })
    }

    @Test
    fun aDeletedBikeIsntShownAndTheRecordIsUnchanged() = testApplication {
        start()
        item("3bike", Visibility.PUBLIC, "Tarmac")
        ride(gear = listOf(gearUri("3bike")))
        remove(Nsid.GEAR_ITEM, Visibility.PUBLIC, "3bike")
        val view = client.view(alice)
        assertNull(view.gear)
        assertNull(view.record.gear)
        assertEquals(mapOf((ActivityGear.SOURCE_RECORD to Visibility.PUBLIC) to listOf(gearUri("3bike"))), activityGear())
    }

    @Test
    fun aNarrowerDetailsListWinsForViewersItAdmits() = testApplication {
        start()
        item("3tarmac", Visibility.PUBLIC, "Tarmac")
        item("3helmet", Visibility.PUBLIC, "Helmet", type = "helmet")
        ride()
        gearDetails(Visibility.FOLLOWERS, listOf(gearUri("3helmet"), gearUri("3tarmac")))
        assertEquals(listOf("Helmet", "Tarmac"), client.view(carol).gear?.map { it.name })
        assertNull(client.view(dave).gear)
    }

    @Test
    fun gearPhotosAreServedByTheirItemsVisibility() = testApplication {
        start()
        item("3pub", Visibility.PUBLIC, "Tarmac", extra = photo)
        ride(gear = listOf(gearUri("3pub")))
        val public = client.view(dave).gear!!.single().photo!!
        assertEquals("${config.publicUrl}/img/media/$alice/$photoCid", public)
        val response = createClient { followRedirects = false }.get(public.removePrefix(config.publicUrl))
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(
            "http://pds.test/xrpc/com.atproto.sync.getBlob?did=${alice.encodeURLParameter()}&cid=$photoCid",
            response.headers[HttpHeaders.Location],
        )
    }

    @Test
    fun aPrivateGearPhotoIsSignedForTheViewer() = testApplication {
        start()
        item("3priv", Visibility.FOLLOWERS, "Tarmac", extra = photo)
        ride(gear = listOf(gearUri("3priv")))
        gearDetails(Visibility.FOLLOWERS, listOf(gearUri("3priv")))
        val url = client.view(carol).gear!!.single().photo!!
        assertTrue(url.contains("viewer=${carol.encodeURLParameter()}"), url)
        val response = client.get(url.removePrefix(config.publicUrl))
        assertEquals(HttpStatusCode.OK, response.status)
        assertContentEquals(photoBytes, response.readRawBytes())
        assertEquals(
            HttpStatusCode.NotFound,
            client.get(url.replace(carol.encodeURLParameter(), dave.encodeURLParameter()).removePrefix(config.publicUrl)).status,
        )
    }
}
