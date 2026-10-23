package io.github.solcott.activitytracker.data.gear

import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.data.FakeAthletePds
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GearCollectionRecord
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** The athlete's gear collections (`gear/gear-collections`): `gear.collection` records in `self` only. */
class GearCollectionsTest {
    private val pds = FakeAthletePds()
    private val did = pds.did
    private val created = Instant.parse("2026-10-23T12:00:00Z")
    private val tarmac = GearItem("3tarmac", GearItemRecord("Tarmac", GearType.BIKE, created), Visibility.PUBLIC)
    private val aeroHelmet = GearItem("3helmet", GearItemRecord("Aero helmet", GearType.HELMET, created), Visibility.PUBLIC)
    private val raceShoes = GearItem("3shoes", GearItemRecord("Race shoes", GearType.SHOES, created), Visibility.SELF)
    private val powerMeter = GearItem("3power", GearItemRecord("Power meter", GearType.COMPONENT, created), Visibility.SELF)
    private val items = listOf(tarmac, aeroHelmet, raceShoes, powerMeter)
    private val raceKit = GearCollectionRecord("Race kit", created, items = items.map { it.uri(did) })

    private suspend fun collections(scope: String? = null) = GearCollections(pds.session(scope).clients, Clock.System)

    @Test
    fun raceKitListsItsItemsInOrderAndIsStoredInSelfOnly() = runTest {
        val repo = collections()
        val saved = repo.save(null, raceKit)

        assertEquals(setOf(Visibility.SELF), pds.placesOf(Nsid.GEAR_COLLECTION, saved.rkey), "nothing about it is in the repo")
        val read = collections().all(items).single()
        assertEquals("Race kit", read.record.name)
        assertEquals(items.map { it.uri(did) }, read.items)
    }

    @Test
    fun anItemCanBeInTwoCollections() = runTest {
        val repo = collections()
        val kit = repo.save(null, raceKit)
        val winter = repo.save(null, GearCollectionRecord("Winter training", created))

        repo.setMembership(powerMeter.uri(did), setOf(kit.rkey, winter.rkey))

        val all = repo.all(items).associateBy { it.record.name }
        assertTrue(powerMeter.uri(did) in all.getValue("Race kit").items)
        assertEquals(listOf(powerMeter.uri(did)), all.getValue("Winter training").items)
        assertEquals(setOf(kit.rkey, winter.rkey), repo.collectionsOf(powerMeter.uri(did)))
    }

    @Test
    fun joiningAddsAtTheEndAndLeavingRemoves() = runTest {
        val repo = collections()
        val kit = repo.save(null, raceKit.copy(items = listOf(tarmac.uri(did))))

        repo.setMembership(aeroHelmet.uri(did), setOf(kit.rkey))
        assertEquals(listOf(tarmac.uri(did), aeroHelmet.uri(did)), repo.all(items).single().items)

        repo.setMembership(aeroHelmet.uri(did), emptySet())
        assertEquals(listOf(tarmac.uri(did)), repo.all(items).single().items)
    }

    @Test
    fun aDeletedMemberIsHidden() = runTest {
        val repo = collections()
        repo.save(null, raceKit)

        val read = repo.all(items - raceShoes).single()

        assertEquals(listOf(tarmac, aeroHelmet, powerMeter).map { it.uri(did) }, read.items)
    }

    @Test
    fun deletingACollectionRemovesOnlyIt() = runTest {
        val repo = collections()
        val kit = repo.save(null, raceKit)

        repo.delete(kit.rkey)

        assertEquals(emptySet(), pds.placesOf(Nsid.GEAR_COLLECTION, kit.rkey))
        assertTrue(repo.all(items).isEmpty())
    }

    @Test
    fun anOlderSessionIsAskedToSignInAgain() = runTest {
        val repo = collections(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}")

        assertFalse(repo.canSave)
        assertFailsWith<GearNeedsSignInException> { repo.save(null, raceKit) }
        assertTrue(pds.writes.isEmpty())
    }
}
