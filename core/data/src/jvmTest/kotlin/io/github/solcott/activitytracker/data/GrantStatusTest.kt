package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.data.auth.GrantFeature
import io.github.solcott.activitytracker.data.auth.GrantStatus
import io.github.solcott.activitytracker.lexicon.AthleteSpaceDeclaration
import io.github.solcott.activitytracker.lexicon.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** Whether a session's sign-in grants everything the app asks for now (auth/account-page). */
class GrantStatusTest {
    private val pds = FakeAthletePds()

    private fun spaceScope(collections: List<String>) =
        "atproto transition:generic space:${Nsid.SPACE_ATHLETE}?" +
            (collections.map { "collection=$it" } + "action=read&action=read_self&action=create&manage=create&manage=update&manage=delete")
                .joinToString("&")

    @Test
    fun everyDeclaredCollectionHasAFeature() {
        assertEquals(emptyList(), AthleteSpaceDeclaration.collections.filter { it !in GrantFeature.byCollection })
    }

    @Test
    fun aGrantListingEveryDeclaredCollectionIsCurrent() = runTest {
        val s = pds.session(scope = spaceScope(AthleteSpaceDeclaration.collections))
        s.clients.resolveSpacesSupport()

        assertEquals(GrantStatus.Current, s.clients.grantStatus)
    }

    @Test
    fun aGrantAsRequestedWithNoCollectionsFilledInIsCurrent() = runTest {
        val s = pds.session()

        assertEquals(GrantStatus.Current, s.clients.grantStatus)
    }

    @Test
    fun noSpaceScopeMissesEverything() = runTest {
        val s = pds.session(scope = "atproto transition:generic")

        assertEquals(GrantStatus.Outdated(GrantFeature.all), s.clients.grantStatus)
    }

    @Test
    fun aGrantMissingOneCollectionMissesItsFeature() = runTest {
        val s = pds.session(scope = spaceScope(AthleteSpaceDeclaration.collections - Nsid.FEED_SEGMENT_NAME_VOTE))

        assertEquals(GrantStatus.Outdated(setOf(GrantFeature.SegmentsAndGroups)), s.clients.grantStatus)
    }

    @Test
    fun aGrantFromBeforeSpaceDeletionMissesLeaving() = runTest {
        val before = spaceScope(AthleteSpaceDeclaration.collections).replace("&manage=delete", "")
        val s = pds.session(scope = before)

        assertEquals(false, s.clients.canDeleteSpaces)
        assertEquals(GrantStatus.Outdated(setOf(GrantFeature.Leaving)), s.clients.grantStatus)
    }

    @Test
    fun theRequestedScopeAsksForSpaceDeletion() {
        assertEquals(true, OAuthClientConfig.DEFAULT_SCOPE.contains("manage=delete"))
    }

    @Test
    fun aPdsWithoutSpacesIsReportedWhateverTheGrant() = runTest {
        pds.spacesAlpha = null
        val s = pds.session(scope = "atproto transition:generic")
        s.clients.resolveSpacesSupport()

        assertEquals(GrantStatus.SpacesUnsupported, s.clients.grantStatus)
    }

    @Test
    fun unknownSupportLeavesItToTheGrant() = runTest {
        pds.describeServerUnreachable = true
        val s = pds.session()
        s.clients.resolveSpacesSupport()

        assertEquals(GrantStatus.Current, s.clients.grantStatus)
    }
}
