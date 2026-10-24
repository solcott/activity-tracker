package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import app.cash.turbine.ReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.activityfile.TimeZones
import io.github.solcott.activitytracker.app.gear.EditGearScreen
import io.github.solcott.activitytracker.app.gear.GearCollectionChoice
import io.github.solcott.activitytracker.app.gear.GearEvent
import io.github.solcott.activitytracker.app.gear.GearPresenter
import io.github.solcott.activitytracker.app.gear.GearScreen
import io.github.solcott.activitytracker.app.gear.GearState
import io.github.solcott.activitytracker.app.gear.LinkedSensor
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GearAutoAssignRecord
import io.github.solcott.activitytracker.lexicon.app.GearCollectionRecord
import io.github.solcott.activitytracker.lexicon.app.GearComponent
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearMount
import io.github.solcott.activitytracker.lexicon.app.GearProfileLink
import io.github.solcott.activitytracker.lexicon.app.GearSensorLink
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.GearUsage
import io.github.solcott.activitytracker.lexicon.app.GearView
import io.github.solcott.activitytracker.lexicon.app.GetGear
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SensorKey
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.uistate.hasLoaded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.json.JsonObject

/** A gear item's page (gear/gear-usage): the owner's with Edit, a follower's without, and an item the viewer may not see. */
class GearPresenterTest {
    private val fixture = SessionTestFixture()
    private val created = Instant.parse("2026-10-20T12:00:00Z")
    private val totals =
        GearTotals(activities = 1, movingTime = 3_600, distance = 40_000, elevationGain = 300, elevationLoss = 300, days = 1)

    private fun item(author: Did, rkey: String = "3tarmac"): String {
        val uri = AtUri.of(author, Nsid.GEAR_ITEM, rkey)
        fixture.gearUsage[uri.value] =
            GetGear.Output(
                gear = GearView(uri, "Tarmac", GearType.BIKE),
                usage = GearUsage(totals, emptyList(), emptyList()),
                retired = false,
                visibility = Visibility.PUBLIC.takeIf { author == fixture.did },
            )
        return uri.value
    }

    private fun ride(author: Did) =
        ActivityView(
            uri = AtUri.of(author, Nsid.FEED_ACTIVITY, "3ride"),
            cid = Cid("bafyride"),
            author = ProfileViewBasic(author, Handle("${author.value.substringAfterLast(':')}.test")),
            record = ActivityRecord(Sport.RIDE, created, 3_600, created, distance = 40_000),
            likeCount = 0,
            commentCount = 0,
            indexedAt = created,
        )

    @Test
    fun theOwnersPageShowsTheTotalsAndActivitiesAndOpensEdit() = runTest {
        val uri = item(fixture.did)
        fixture.gearActivities = listOf(ride(fixture.did))
        val repos = fixture.repos()
        val navigator = FakeNavigator(GearScreen(uri))
        presenterTestOf({
            GearPresenter(GearScreen(uri), navigator, repos.gear, repos.gearCollections, repos.gearDefaults, repos.activities, clock)
        }) {
            val loaded = awaitUntil { it.content.hasLoaded && it.activities.items.isNotEmpty() }
            val page = checkNotNull(loaded.page)
            assertTrue(page.isOwner)
            assertEquals(Visibility.PUBLIC, page.visibility)
            assertEquals(40_000, page.usage.totals.distance)
            assertEquals(listOf("3ride"), loaded.activities.items.map { it.uri.rkey })
            loaded.eventSink(GearEvent.Edit)
            assertEquals(EditGearScreen("3tarmac"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFollowersPageHasNoEdit() = runTest {
        val bob = Did("did:plc:bob")
        val uri = item(bob)
        fixture.gearActivities = listOf(ride(bob))
        val repos = fixture.repos()
        val navigator = FakeNavigator(GearScreen(uri))
        presenterTestOf({
            GearPresenter(GearScreen(uri), navigator, repos.gear, repos.gearCollections, repos.gearDefaults, repos.activities, clock)
        }) {
            val loaded = awaitUntil { it.content.hasLoaded && it.activities.items.isNotEmpty() }
            val page = checkNotNull(loaded.page)
            assertFalse(page.isOwner)
            assertNull(page.visibility)
            loaded.eventSink(GearEvent.Edit)
            navigator.expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anItemTheViewerMayNotSeeIsNotFound() = runTest {
        val uri = AtUri.of(Did("did:plc:bob"), Nsid.GEAR_ITEM, "3private").value
        val repos = fixture.repos()
        presenterTestOf({
            GearPresenter(
                GearScreen(uri),
                FakeNavigator(GearScreen(uri)),
                repos.gear,
                repos.gearCollections,
                repos.gearDefaults,
                repos.activities,
                clock,
            )
        }) {
            val state = awaitUntil { it.notFound }
            assertFalse(state.content.hasLoaded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun raceKit(vararg members: String) {
        fixture.gearCollections["3kit"] =
            AtJson.encodeToJsonElement(
                GearCollectionRecord.serializer(),
                GearCollectionRecord("Race kit", created, items = members.map(::AtUri)),
            ) as JsonObject
    }

    private fun raceKitItems() =
        AtJson.decodeFromJsonElement(GearCollectionRecord.serializer(), fixture.gearCollections.getValue("3kit")).items.orEmpty()

    @Test
    fun theOwnerAddsTheHelmetToRaceKitFromItsPageAndTakesItOut() = runTest {
        val tarmac = item(fixture.did)
        val helmet = item(fixture.did, "3helmet")
        raceKit(tarmac)
        val repos = fixture.repos()
        presenterTestOf({
            GearPresenter(
                GearScreen(helmet),
                FakeNavigator(GearScreen(helmet)),
                repos.gear,
                repos.gearCollections,
                repos.gearDefaults,
                repos.activities,
                clock,
            )
        }) {
            val loaded = awaitUntil { it.collections.isNotEmpty() }
            assertEquals(listOf(GearCollectionChoice("3kit", "Race kit", member = false)), loaded.collections)
            loaded.eventSink(GearEvent.ToggleCollection("3kit"))
            val joined = awaitUntil { it.collections.single().member }
            assertEquals(listOf(AtUri(tarmac), AtUri(helmet)), raceKitItems(), "at the end")
            joined.eventSink(GearEvent.ToggleCollection("3kit"))
            awaitUntil { !it.collections.single().member }
            assertEquals(listOf(AtUri(tarmac)), raceKitItems())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFollowerNeverSeesTheOwnersCollections() = runTest {
        val bob = Did("did:plc:bob")
        val uri = item(bob)
        raceKit(uri)
        val repos = fixture.repos()
        presenterTestOf({
            GearPresenter(
                GearScreen(uri),
                FakeNavigator(GearScreen(uri)),
                repos.gear,
                repos.gearCollections,
                repos.gearDefaults,
                repos.activities,
                clock,
            )
        }) {
            val loaded = awaitUntil { it.content.hasLoaded }
            assertTrue(loaded.collections.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private val clock =
        object : Clock {
            override fun now() = Instant.parse("2026-07-15T12:00:00Z")
        }
    private val zone = TimeZones.device()
    private val today = LocalDate(2026, 7, 15).atStartOfDayIn(zone)
    private val march = Instant.parse("2026-03-01T00:00:00Z")

    private fun store(rkey: String, record: GearItemRecord) {
        fixture.gear.getOrPut("repo") { mutableMapOf() }[rkey] =
            AtJson.encodeToJsonElement(GearItemRecord.serializer(), record) as JsonObject
    }

    private fun stored(rkey: String): GearItemRecord =
        AtJson.decodeFromJsonElement(GearItemRecord.serializer(), fixture.gear.getValue("repo").getValue(rkey))

    private fun chain(name: String, vararg mounts: GearMount) =
        GearItemRecord(name, GearType.COMPONENT, created, kind = "chain", mounts = mounts.toList().ifEmpty { null })

    /** The Tarmac's page, with [components] mounted on it now. */
    private fun tarmacWith(vararg components: String): AtUri {
        val tarmac = AtUri(item(fixture.did))
        store("3tarmac", GearItemRecord("Tarmac", GearType.BIKE, created))
        fixture.gearUsage[tarmac.value] =
            fixture.gearUsage
                .getValue(tarmac.value)
                .copy(
                    components =
                        components.map {
                            GearComponent(GearView(AtUri.of(fixture.did, Nsid.GEAR_ITEM, it), it, GearType.COMPONENT), march, totals)
                        }
                )
        return tarmac
    }

    private suspend fun ReceiveTurbine<GearState>.loaded() = awaitUntil { it.content.hasLoaded }

    @Composable
    private fun presenter(uri: AtUri, repos: SessionTestFixture.Repos) =
        GearPresenter(
            GearScreen(uri.value),
            FakeNavigator(GearScreen(uri.value)),
            repos.gear,
            repos.gearCollections,
            repos.gearDefaults,
            repos.activities,
            clock,
        )

    @Test
    fun theOwnerMountsAChainOnTheBikeFromToday() = runTest {
        val tarmac = tarmacWith()
        store("3chain", chain("Chain #3"))
        store("3stumpy", GearItemRecord("Stumpjumper", GearType.BIKE, created))
        val repos = fixture.repos()
        presenterTestOf({ presenter(tarmac, repos) }) {
            loaded().eventSink(GearEvent.Mount)
            val picking = awaitUntil { !it.picker?.candidates.isNullOrEmpty() }
            assertEquals(listOf("Chain #3", "Stumpjumper"), picking.picker!!.candidates.map { it.name })
            picking.eventSink(GearEvent.ChooseMount("3chain"))
            awaitUntil { it.picker == null && !it.mountSaving }
            assertEquals(listOf(GearMount(tarmac, today)), stored("3chain").mounts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun replacingAChainEndsItsMountTodayAndMountsTheNewOneFromToday() = runTest {
        val tarmac = tarmacWith("3chain3")
        store("3chain3", chain("Chain #3", GearMount(tarmac, march)))
        store("3chain4", chain("Chain #4"))
        val repos = fixture.repos()
        presenterTestOf({ presenter(tarmac, repos) }) {
            val chain3 = AtUri.of(fixture.did, Nsid.GEAR_ITEM, "3chain3")
            loaded().eventSink(GearEvent.Replace(chain3.value))
            val picking = awaitUntil { !it.picker?.candidates.isNullOrEmpty() }
            val picker = checkNotNull(picking.picker)
            assertEquals(chain3, picker.replacing?.gear?.uri)
            assertEquals(listOf("Chain #4"), picker.candidates.map { it.name })
            picking.eventSink(GearEvent.ChooseMount("3chain4"))
            awaitUntil { it.picker == null && !it.mountSaving }
            assertEquals(listOf(GearMount(tarmac, march, today)), stored("3chain3").mounts)
            assertEquals(listOf(GearMount(tarmac, today)), stored("3chain4").mounts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unmountingEndsTheMountOnTheChosenDay() = runTest {
        val tarmac = tarmacWith("3chain3")
        store("3chain3", chain("Chain #3", GearMount(tarmac, march)))
        val repos = fixture.repos()
        presenterTestOf({ presenter(tarmac, repos) }) {
            loaded().eventSink(GearEvent.Unmount(AtUri.of(fixture.did, Nsid.GEAR_ITEM, "3chain3").value))
            val asking = awaitUntil { it.unmounting != null }
            asking.eventSink(GearEvent.ConfirmUnmount(LocalDate(2026, 6, 1)))
            awaitUntil { it.unmounting == null && !it.mountSaving }
            assertEquals(listOf(GearMount(tarmac, march, LocalDate(2026, 6, 1).atStartOfDayIn(zone))), stored("3chain3").mounts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun mountingAChainStillOnAnotherBikeShowsAnOverlapError() = runTest {
        val tarmac = tarmacWith()
        store("3stumpy", GearItemRecord("Stumpjumper", GearType.BIKE, created))
        store("3chain", chain("Chain #3", GearMount(AtUri.of(fixture.did, Nsid.GEAR_ITEM, "3stumpy"), march)))
        val repos = fixture.repos()
        presenterTestOf({ presenter(tarmac, repos) }) {
            loaded().eventSink(GearEvent.Mount)
            val picking = awaitUntil { !it.picker?.candidates.isNullOrEmpty() }
            // A chain mounted elsewhere isn't offered; choosing it anyway is refused.
            assertTrue(picking.picker!!.candidates.none { it.rkey == "3chain" })
            picking.eventSink(GearEvent.ChooseMount("3chain"))
            assertEquals(UiError.Kind.MountOverlap, awaitUntil { it.error != null }.error?.kind)
            assertEquals(listOf(GearMount(AtUri.of(fixture.did, Nsid.GEAR_ITEM, "3stumpy"), march)), stored("3chain").mounts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theOwnerUnlinksTheSoldPowerMeterAndKeepsTheProfile() = runTest {
        val tarmac = AtUri(item(fixture.did))
        val meter = SensorKey(manufacturer = "SRAM", serialNumber = "3236233426", model = "1052")
        val strap = SensorKey(manufacturer = "Garmin", model = "HRM 200")
        val other = AtUri.of(fixture.did, Nsid.GEAR_ITEM, "3other")
        fixture.gearAutoAssign =
            AtJson.encodeToString(
                GearAutoAssignRecord.serializer(),
                GearAutoAssignRecord(
                    created,
                    sensors =
                        listOf(
                            GearSensorLink(meter, tarmac),
                            GearSensorLink(strap, tarmac),
                            GearSensorLink(meter.copy(serialNumber = "9"), other),
                        ),
                    profiles = listOf(GearProfileLink("ROAD", tarmac)),
                ),
            )
        val repos = fixture.repos()
        presenterTestOf({ presenter(tarmac, repos) }) {
            val loaded = awaitUntil { it.linkedSensors.size == 2 }
            assertEquals(
                listOf(LinkedSensor(meter.id, "SRAM", "1052", "3426"), LinkedSensor(strap.id, "Garmin", "HRM 200", weak = true)),
                loaded.linkedSensors.toList(),
                "only this item's links",
            )
            assertEquals(listOf("ROAD"), loaded.linkedProfiles.toList())
            loaded.eventSink(GearEvent.UnlinkSensor(meter.id))
            assertEquals(listOf(strap.id), awaitUntil { it.linkedSensors.size == 1 }.linkedSensors.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        val saved = AtJson.decodeFromString(GearAutoAssignRecord.serializer(), requireNotNull(fixture.gearAutoAssign))
        assertEquals(listOf(strap, meter.copy(serialNumber = "9")), saved.sensors?.map { it.key }, "the other item's link stays")
        assertEquals(listOf(GearProfileLink("ROAD", tarmac)), saved.profiles)
        assertTrue(fixture.putRecords.none { it["\$type"]?.toString()?.contains(Nsid.FEED_ACTIVITY) == true }, "no activity changes")
    }

    @Test
    fun anotherAthletesPageListsNoLinks() = runTest {
        val bob = AtUri(item(Did("did:plc:bob")))
        fixture.gearAutoAssign =
            AtJson.encodeToString(
                GearAutoAssignRecord.serializer(),
                GearAutoAssignRecord(created, sensors = listOf(GearSensorLink(SensorKey(manufacturer = "SRAM", serialNumber = "1"), bob))),
            )
        val repos = fixture.repos()
        presenterTestOf({ presenter(bob, repos) }) {
            val loaded = loaded()
            assertEquals(emptyList(), loaded.linkedSensors.toList())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
