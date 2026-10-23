package io.github.solcott.activitytracker.data.gear

import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.FakeAthletePds
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.GearAutoAssignRecord
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearSportDefault
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

/** Defaults per activity type (`gear/activity-gear`): the `gear.autoAssign` record in `self`, and how a type resolves. */
class GearDefaultsTest {
    private val pds = FakeAthletePds()
    private val did = pds.did
    private val created = Instant.parse("2026-10-20T12:00:00Z")
    private val tarmac = GearItem("3tarmac", GearItemRecord("Tarmac", GearType.BIKE, created), Visibility.PUBLIC)
    private val stumpjumper = GearItem("3stumpy", GearItemRecord("Stumpjumper", GearType.BIKE, created), Visibility.PUBLIC)
    private val helmet = GearItem("3helmet", GearItemRecord("Helmet", GearType.HELMET, created), Visibility.SELF)
    private val items = listOf(tarmac, stumpjumper, helmet)

    private fun uri(item: GearItem): AtUri = item.uri(did)

    private val ride = ActivityType(Sport.RIDE)
    private val mountain = ActivityType(Sport.RIDE, SubSport.MOUNTAIN)

    private val rules =
        GearAutoAssignRecord(
            updatedAt = created,
            sports =
                listOf(
                    GearSportDefault(Sport.RIDE, listOf(uri(tarmac), uri(helmet))),
                    GearSportDefault(Sport.RIDE, listOf(uri(stumpjumper)), subSport = SubSport.MOUNTAIN),
                ),
        )

    private suspend fun defaults(scope: String? = null) = GearDefaults(pds.session(scope).clients, Clock.System)

    @Test
    fun aSportDefaultStartsTheSport() {
        assertEquals(listOf(uri(tarmac), uri(helmet)), GearDefaults.forType(rules, ride, items, did))
    }

    @Test
    fun aSubSportDefaultReplacesTheSportsOfTheSameGearTypeOnly() {
        assertEquals(listOf(uri(stumpjumper), uri(helmet)), GearDefaults.forType(rules, mountain, items, did))
        // Gravel has no entry of its own: the ride defaults.
        assertEquals(listOf(uri(tarmac), uri(helmet)), GearDefaults.forType(rules, ActivityType(Sport.RIDE, SubSport.GRAVEL), items, did))
    }

    @Test
    fun retiredAndDeletedItemsAreNoDefaults() {
        val retired = tarmac.copy(record = tarmac.record.copy(retiredAt = created))
        assertEquals(listOf(uri(helmet)), GearDefaults.forType(rules, ride, listOf(retired, stumpjumper, helmet), did))
        assertEquals(listOf(uri(tarmac)), GearDefaults.forType(rules, ride, listOf(tarmac), did), "the helmet was deleted")
        assertEquals(emptyList(), GearDefaults.forType(rules, ActivityType(Sport.RUN), items, did))
    }

    @Test
    fun settingTypesWritesTheRecordInSelfOnly() = runTest {
        val repo = defaults()
        repo.setTypes(uri(tarmac), listOf(ride))
        repo.setTypes(uri(stumpjumper), listOf(mountain))
        repo.setTypes(uri(helmet), listOf(ride))

        assertEquals(setOf(Visibility.SELF), pds.placesOf(Nsid.GEAR_AUTO_ASSIGN, GearAutoAssignRecord.RKEY))
        val stored =
            AtJson.decodeFromJsonElement(
                GearAutoAssignRecord.serializer(),
                pds.stored.getValue(Visibility.SELF).getValue("${Nsid.GEAR_AUTO_ASSIGN}/self") as JsonObject,
            )
        assertEquals(rules.sports, stored.sports)
        assertEquals(listOf(uri(stumpjumper), uri(helmet)), defaults().forType(mountain, items), "another device reads the same")
        assertEquals(listOf(mountain), repo.typesFor(uri(stumpjumper)))
    }

    @Test
    fun clearingAnItemsTypesDropsEmptyEntries() = runTest {
        val repo = defaults()
        repo.setTypes(uri(stumpjumper), listOf(mountain))
        val after = repo.setTypes(uri(stumpjumper), emptyList())
        assertEquals(null, after.sports)
    }

    @Test
    fun anOlderSessionIsAskedToSignInAgain() = runTest {
        val repo = defaults(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}")
        val e = assertFailsWith<XrpcException> { repo.setTypes(uri(tarmac), listOf(ride)) }
        assertEquals(SpaceErrors.SCOPE_MISSING, e.error)
        assertEquals(emptySet(), pds.placesOf(Nsid.GEAR_AUTO_ASSIGN, GearAutoAssignRecord.RKEY))
    }
}
