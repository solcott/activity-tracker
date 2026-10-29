package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.segments.SegmentNameRules
import io.github.solcott.activitytracker.lexicon.Did
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

    @Test
    fun theSegmentImporterIsOptionalAndMustBeADid() {
        assertEquals(null, AppViewConfig.fromEnv(emptyMap()).segmentImporter)
        assertEquals(null, AppViewConfig.fromEnv(mapOf("SEGMENT_IMPORTER_DID" to " ")).segmentImporter)
        assertEquals(null, AppViewConfig.fromEnv(mapOf("SEGMENT_IMPORTER_DID" to "importer.test")).segmentImporter)
        assertEquals(Did("did:plc:importer"), AppViewConfig.fromEnv(mapOf("SEGMENT_IMPORTER_DID" to " did:plc:importer\n")).segmentImporter)
    }

    @Test
    fun segmentCandidateRidersDefaultsToThreeAndIsAtLeastOne() {
        assertEquals(3, AppViewConfig.fromEnv(emptyMap()).segmentCandidateRiders)
        assertEquals(5, AppViewConfig.fromEnv(mapOf("SEGMENT_CANDIDATE_RIDERS" to " 5 ")).segmentCandidateRiders)
        assertEquals(1, AppViewConfig.fromEnv(mapOf("SEGMENT_CANDIDATE_RIDERS" to "-2")).segmentCandidateRiders)
        assertEquals(3, AppViewConfig.fromEnv(mapOf("SEGMENT_CANDIDATE_RIDERS" to "")).segmentCandidateRiders)
        assertEquals(3, AppViewConfig.fromEnv(mapOf("SEGMENT_CANDIDATE_RIDERS" to "lots")).segmentCandidateRiders)
    }

    @Test
    fun segmentNameRulesHaveDefaultsAndIgnoreInvalidValues() {
        assertEquals(SegmentNameRules(), AppViewConfig.fromEnv(emptyMap()).segmentNames)
        val custom =
            AppViewConfig.fromEnv(
                    mapOf(
                        "SEGMENT_NAME_PROPOSER_DAYS" to "2",
                        "SEGMENT_NAME_MIN_UP" to " 1 ",
                        "SEGMENT_NAME_RIDER_PERCENT" to "0",
                        "SEGMENT_NAME_MIN_OPEN_DAYS" to "0",
                        "SEGMENT_NAME_REJECT_SCORE" to "-2",
                        "SEGMENT_NAME_EXPIRY_DAYS" to "30",
                        "SEGMENT_NAME_COOLDOWN_DAYS" to "0",
                    )
                )
                .segmentNames
        assertEquals(SegmentNameRules(2, 1, 0, 0, -2, 30, 0), custom)
        val invalid =
            AppViewConfig.fromEnv(
                    mapOf(
                        "SEGMENT_NAME_PROPOSER_DAYS" to "0",
                        "SEGMENT_NAME_MIN_UP" to "lots",
                        "SEGMENT_NAME_RIDER_PERCENT" to "150",
                        "SEGMENT_NAME_MIN_OPEN_DAYS" to "-1",
                        "SEGMENT_NAME_REJECT_SCORE" to "3",
                        "SEGMENT_NAME_EXPIRY_DAYS" to "",
                        "SEGMENT_NAME_COOLDOWN_DAYS" to "-5",
                    )
                )
                .segmentNames
        assertEquals(SegmentNameRules(), invalid)
    }

    @Test
    fun upNeededIsTheLargerOfTheMinimumAndTheRiderShareRoundedUp() {
        val rules = SegmentNameRules()
        assertEquals(5, rules.upNeeded(20))
        assertEquals(30, rules.upNeeded(300))
        assertEquals(31, rules.upNeeded(301))
    }

    @Test
    fun privateAddressesAreRefusedUnlessAllowed() {
        assertEquals(false, AppViewConfig.fromEnv(emptyMap()).allowPrivateAddresses)
        assertEquals(true, AppViewConfig.fromEnv(mapOf("APPVIEW_ALLOW_PRIVATE_ADDRESSES" to "true")).allowPrivateAddresses)
        assertEquals(false, AppViewConfig.fromEnv(mapOf("APPVIEW_ALLOW_PRIVATE_ADDRESSES" to "false")).allowPrivateAddresses)
    }
}
