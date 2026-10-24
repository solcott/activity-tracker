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
import io.github.solcott.activitytracker.lexicon.app.GearProfileLink
import io.github.solcott.activitytracker.lexicon.app.GearSensorLink
import io.github.solcott.activitytracker.lexicon.app.GearSportDefault
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.SensorKey
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

    private val meter = SensorKey(manufacturer = "SRAM", serialNumber = "3236233426")
    private val edge = SensorKey(manufacturer = "Garmin", serialNumber = "3510682071")

    @Test
    fun linkingADeviceRemovesItsIgnore() = runTest {
        val repo = defaults()
        repo.editLinks(GearLinkEdit(ignore = listOf(edge)))
        val after =
            repo.editLinks(
                GearLinkEdit(link = listOf(GearSensorLink(SensorKey(manufacturer = "garmin", serialNumber = "3510682071"), uri(tarmac))))
            )
        assertEquals(null, after.ignored)
        assertEquals(listOf(uri(tarmac)), after.sensors?.map { it.gear })
        assertEquals(after.sensors, defaults().load().sensors, "stored in self")
        assertEquals(setOf(Visibility.SELF), pds.placesOf(Nsid.GEAR_AUTO_ASSIGN, GearAutoAssignRecord.RKEY))
    }

    @Test
    fun ignoringADeviceRemovesItsLink() = runTest {
        val repo = defaults()
        repo.editLinks(GearLinkEdit(link = listOf(GearSensorLink(meter, uri(tarmac)))))
        val after = repo.editLinks(GearLinkEdit(ignore = listOf(meter)))
        assertEquals(null, after.sensors)
        assertEquals(listOf(meter), after.ignored)
    }

    @Test
    fun aKeyLinksToOneItemAndUnlinkingLeavesTheRest() = runTest {
        val repo = defaults()
        repo.setTypes(uri(tarmac), listOf(ride))
        repo.editLinks(GearLinkEdit(link = listOf(GearSensorLink(meter, uri(tarmac)), GearSensorLink(edge, uri(tarmac)))))
        val relinked = repo.editLinks(GearLinkEdit(link = listOf(GearSensorLink(meter, uri(stumpjumper)))))
        assertEquals(listOf(edge to uri(tarmac), meter to uri(stumpjumper)), relinked.sensors?.map { it.key to it.gear })
        val after = repo.editLinks(GearLinkEdit(unlink = listOf(meter)))
        assertEquals(listOf(edge), after.sensors?.map { it.key })
        assertEquals(listOf(uri(tarmac)), after.sports?.single()?.gear, "defaults are kept")
    }

    @Test
    fun aProfileLinksToOneItem() = runTest {
        val repo = defaults()
        repo.editLinks(GearLinkEdit(linkProfiles = listOf(GearProfileLink("MOUNTAIN", uri(tarmac)))))
        val after = repo.editLinks(GearLinkEdit(linkProfiles = listOf(GearProfileLink("MOUNTAIN", uri(stumpjumper)))))
        assertEquals(listOf(GearProfileLink("MOUNTAIN", uri(stumpjumper))), after.profiles)
        assertEquals(null, repo.editLinks(GearLinkEdit(unlinkProfiles = setOf("MOUNTAIN"))).profiles)
    }

    @Test
    fun anOlderSessionCantLink() = runTest {
        val repo = defaults(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}")
        val e = assertFailsWith<XrpcException> { repo.editLinks(GearLinkEdit(ignore = listOf(edge))) }
        assertEquals(SpaceErrors.SCOPE_MISSING, e.error)
    }
}
