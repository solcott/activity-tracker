package io.github.solcott.activitytracker.appview

import kotlin.test.Test
import kotlin.test.assertEquals

class AppViewConfigTest {
    @Test
    fun managingAppServiceDefaultsToLocalhostDid() {
        assertEquals("did:web:localhost%3A8081#activitytracker_space", AppViewConfig.fromEnv(emptyMap()).managingAppService)
    }

    @Test
    fun managingAppServiceFollowsPublicUrl() {
        val config = AppViewConfig.fromEnv(mapOf("APPVIEW_PUBLIC_URL" to "https://quick-tunnel.trycloudflare.com"))
        assertEquals("did:web:quick-tunnel.trycloudflare.com#activitytracker_space", config.managingAppService)
    }

    @Test
    fun managingAppServiceFollowsExplicitDid() {
        val config = AppViewConfig.fromEnv(mapOf("APPVIEW_DID" to "did:web:appview.example.com"))
        assertEquals("did:web:appview.example.com#activitytracker_space", config.managingAppService)
    }

    @Test
    fun syncerServiceFollowsServiceDid() {
        val config = AppViewConfig.fromEnv(mapOf("APPVIEW_DID" to "did:web:appview.example.com"))
        assertEquals("did:web:appview.example.com#activitytracker_space_syncer", config.syncerService)
    }

    @Test
    fun signingKeyIsOptional() {
        assertEquals(null, AppViewConfig.fromEnv(emptyMap()).signingKey)
        assertEquals(null, AppViewConfig.fromEnv(mapOf("APPVIEW_SIGNING_KEY" to "  ")).signingKey)
        assertEquals(TEST_SIGNING_KEY, AppViewConfig.fromEnv(mapOf("APPVIEW_SIGNING_KEY" to " $TEST_SIGNING_KEY\n")).signingKey)
    }

    @Test
    fun labelersAreOptional() {
        val none = AppViewConfig.fromEnv(emptyMap())
        assertEquals(null, none.labeler)
        assertEquals(null, none.bskyLabeler)
        assertEquals(null, AppViewConfig.fromEnv(mapOf("LABELER_DID" to " ", "LABELER_URL" to "http://localhost:3010")).labeler)
    }

    @Test
    fun labelersComeFromTheirDidAndUrl() {
        val config =
            AppViewConfig.fromEnv(
                mapOf(
                    "LABELER_DID" to "did:plc:labeler",
                    "LABELER_URL" to "http://localhost:3010/",
                    "BSKY_LABELER_DID" to "did:plc:ar7c4by46qjdydhdevvrndac",
                )
            )
        assertEquals(AppViewConfig.Labeler("did:plc:labeler", "http://localhost:3010"), config.labeler)
        // No URL: it's resolved from the DID document's #atproto_labeler.
        assertEquals(AppViewConfig.Labeler("did:plc:ar7c4by46qjdydhdevvrndac"), config.bskyLabeler)
    }

    @Test
    fun theTriageAccountNeedsADidAndAnAppPassword() {
        assertEquals(null, AppViewConfig.fromEnv(mapOf("APPVIEW_TRIAGE_DID" to "did:plc:triage")).triage)
        val config =
            AppViewConfig.fromEnv(mapOf("APPVIEW_TRIAGE_DID" to "did:plc:triage", "APPVIEW_TRIAGE_PASSWORD" to "abcd-efgh-ijkl-mnop"))
        assertEquals(AppViewConfig.Triage("did:plc:triage", "abcd-efgh-ijkl-mnop"), config.triage)
        assertEquals("Triage(did:plc:triage)", config.triage.toString(), "the password stays out of logs")
    }

    @Test
    fun segmentActiveAthletesDefaultsToThreeAndIsAtLeastOne() {
        assertEquals(3, AppViewConfig.fromEnv(emptyMap()).segmentActiveAthletes)
        assertEquals(2, AppViewConfig.fromEnv(mapOf("SEGMENT_ACTIVE_ATHLETES" to " 2 ")).segmentActiveAthletes)
        assertEquals(1, AppViewConfig.fromEnv(mapOf("SEGMENT_ACTIVE_ATHLETES" to "0")).segmentActiveAthletes)
        assertEquals(3, AppViewConfig.fromEnv(mapOf("SEGMENT_ACTIVE_ATHLETES" to "")).segmentActiveAthletes)
        assertEquals(3, AppViewConfig.fromEnv(mapOf("SEGMENT_ACTIVE_ATHLETES" to "many")).segmentActiveAthletes)
    }
}
