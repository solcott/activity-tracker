package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import app.cash.turbine.ReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.DecodedActivity
import io.github.solcott.activitytracker.activityfile.DecodedSample
import io.github.solcott.activitytracker.activityfile.DecodedSensor
import io.github.solcott.activitytracker.activityfile.DecodedSource
import io.github.solcott.activitytracker.activityfile.DecodedSummary
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.record.ImportActivityEvent
import io.github.solcott.activitytracker.app.record.ImportActivityPresenter
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.app.record.ImportActivityState
import io.github.solcott.activitytracker.app.record.NewDeviceDecision
import io.github.solcott.activitytracker.app.record.NewDeviceEvent
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityDevice
import io.github.solcott.activitytracker.lexicon.app.ActivityDevicesRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DeviceRole
import io.github.solcott.activitytracker.lexicon.app.GearAutoAssignRecord
import io.github.solcott.activitytracker.lexicon.app.GearCollectionRecord
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearMount
import io.github.solcott.activitytracker.lexicon.app.GearProfileLink
import io.github.solcott.activitytracker.lexicon.app.GearSensorLink
import io.github.solcott.activitytracker.lexicon.app.GearSportDefault
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SensorKey
import io.github.solcott.activitytracker.lexicon.app.SensorType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Sensors and activity profiles filling in an import's gear, and the new devices it asks about (gear/gear-sensors). */
class ImportSensorGearTest {
    private val fixture = SessionTestFixture()
    private val start = Instant.parse("2026-09-20T07:00:00Z")
    private val created = Instant.parse("2026-01-01T00:00:00Z")

    /** Just a FIT header: the reader picks the decoder by the ".FIT" signature at byte 8, and the fake decoder ignores the rest. */
    private val bytes = ByteString(14, 16, 0, 0, 0, 0, 0, 0, '.'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'T'.code.toByte())

    private val tarmac = gearUri("3tarmac")
    private val stumpjumper = gearUri("3stumpy")
    private val helmet = gearUri("3helmet")

    private val edgeKey = SensorKey(manufacturer = "Garmin", serialNumber = "3510682071")
    private val meterKey = SensorKey(manufacturer = "SRAM", serialNumber = "3236233426")
    private val strapKey = SensorKey(manufacturer = "Garmin", model = "HRM 200")

    private fun gearUri(rkey: String) = AtUri("at://${fixture.did.value}/${Nsid.GEAR_ITEM}/$rkey")

    private fun storeGear(rkey: String, record: GearItemRecord, place: String = "repo") {
        fixture.gear.getOrPut(place) { mutableMapOf() }[rkey] =
            AtJson.encodeToJsonElement(GearItemRecord.serializer(), record) as JsonObject
    }

    private fun storeBikes() {
        storeGear("3tarmac", GearItemRecord("Tarmac", GearType.BIKE, created))
        storeGear("3stumpy", GearItemRecord("Stumpjumper", GearType.BIKE, created))
        storeGear("3helmet", GearItemRecord("Helmet", GearType.HELMET, created))
    }

    private fun rules(vararg links: GearSensorLink, profiles: List<GearProfileLink>? = null, ignored: List<SensorKey>? = null) {
        fixture.gearAutoAssign =
            AtJson.encodeToString(
                GearAutoAssignRecord.serializer(),
                GearAutoAssignRecord(
                    created,
                    sports = listOf(GearSportDefault(Sport.RIDE, listOf(tarmac, helmet))),
                    sensors = links.toList().ifEmpty { null },
                    profiles = profiles,
                    ignored = ignored,
                ),
            )
    }

    private val savedRules: GearAutoAssignRecord
        get() = AtJson.decodeFromString(GearAutoAssignRecord.serializer(), requireNotNull(fixture.gearAutoAssign))

    private fun presenter(repos: SessionTestFixture.Repos, navigator: FakeNavigator, ride: DecodedActivity = ride()) =
        @Composable {
            ImportActivityPresenter(
                ImportActivityScreen(),
                navigator,
                repos.activities,
                repos.manager,
                ActivityFileReader { ride },
                repos.typeChoices,
                repos.gearChoices,
                repos.newDeviceChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
            )
        }

    /** The gear the published ride lists, from its record or, at a narrower Gear category, its details. */
    private fun publishedGear(): List<AtUri> {
        val details = fixture.spacePuts.map { it.second }.lastOrNull { it["\$type"]?.jsonPrimitive?.content == Nsid.FEED_ACTIVITY_DETAILS }
        val record = fixture.putRecords.lastOrNull { it["\$type"]?.jsonPrimitive?.content == Nsid.FEED_ACTIVITY }
        return details?.let { AtJson.decodeFromJsonElement(ActivityDetailsRecord.serializer(), it).gear }
            ?: record?.let { AtJson.decodeFromJsonElement(ActivityRecord.serializer(), it).gear }.orEmpty()
    }

    private suspend fun ReceiveTurbine<ImportActivityState>.picked(): ImportActivityState {
        awaitItem().eventSink(ImportActivityEvent.FilePicked("ride.fit", bytes))
        return awaitUntil { it.preview != null && it.gear?.selected?.isNotEmpty() == true }
    }

    /** [state] if it already matches [predicate], else the next state that does. */
    private suspend fun ReceiveTurbine<ImportActivityState>.awaitFrom(
        state: ImportActivityState,
        predicate: (ImportActivityState) -> Boolean,
    ): ImportActivityState = if (predicate(state)) state else awaitUntil(predicate)

    @Test
    fun aLinkedCrankBeatsTheRideDefault() = runTest {
        storeBikes()
        storeGear("3crank", GearItemRecord("Crank", GearType.COMPONENT, created, mounts = listOf(GearMount(stumpjumper, created))))
        rules(GearSensorLink(meterKey, gearUri("3crank")))
        val repos = fixture.repos()
        presenterTestOf(presenter(repos, FakeNavigator(ImportActivityScreen()))) {
            val read = picked()
            assertEquals(listOf(stumpjumper, helmet), read.gear?.selected?.toList(), "the Stumpjumper the crank is on, and the helmet")
            assertEquals(emptyList(), read.gear?.conflicts?.toList())
            assertEquals(2, read.newDevices.cards.size, "the power meter is linked; the Edge and the strap aren't")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aLinkedProfileFillsInTheBikeWhenNoSensorMatches() = runTest {
        storeBikes()
        rules(profiles = listOf(GearProfileLink("MOUNTAIN", stumpjumper)))
        val repos = fixture.repos()
        presenterTestOf(presenter(repos, FakeNavigator(ImportActivityScreen()), ride(profile = "MOUNTAIN"))) {
            val read = picked()
            assertEquals(listOf(stumpjumper, helmet), read.gear?.selected?.toList())
            assertNull(read.newDevices.profileOffer, "MOUNTAIN is linked already")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sensorsMatchingTwoBikesAreBothKeptAndFlagged() = runTest {
        storeBikes()
        storeGear("3pedals", GearItemRecord("Power pedals", GearType.COMPONENT, created, mounts = listOf(GearMount(tarmac, created))))
        rules(GearSensorLink(meterKey, gearUri("3pedals")), GearSensorLink(edgeKey, stumpjumper))
        val repos = fixture.repos()
        presenterTestOf(presenter(repos, FakeNavigator(ImportActivityScreen()))) {
            val read = awaitFrom(picked()) { it.gear?.conflicts?.isNotEmpty() == true }
            assertEquals(setOf(tarmac, stumpjumper, helmet), read.gear?.selected?.toSet())
            val conflict = read.gear?.conflicts?.single()
            assertEquals(GearType.BIKE, conflict?.type)
            assertEquals(setOf("Tarmac", "Stumpjumper"), conflict?.names?.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anEdge840RideOffersItsHeadUnitStrapAndPowerMeter() = runTest {
        storeBikes()
        rules()
        val repos = fixture.repos()
        presenterTestOf(presenter(repos, FakeNavigator(ImportActivityScreen()))) {
            val cards = awaitFrom(picked()) { it.newDevices.cards.size == 3 }.newDevices.cards
            assertEquals(listOf("Edge 840", "HRM 200", "1052"), cards.map { it.model }, "the head unit first")
            assertTrue(cards[0].recorder)
            assertEquals(GearType.DEVICE, cards[0].type)
            assertEquals("2071", cards[0].serialTail)
            assertTrue(cards[1].weak, "the strap is known only by its model")
            assertEquals(GearType.SENSOR to "heartRate", cards[1].type to cards[1].kind)
            assertEquals(GearType.COMPONENT to "powerMeter", cards[2].type to cards[2].kind)
            assertEquals("SRAM 1052", cards[2].name.text.toString())
            assertTrue(cards.all { it.decision == NewDeviceDecision.Later }, "Later until the athlete answers")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRideRecordedUnderAnUnlinkedProfileOffersToLinkIt() = runTest {
        storeBikes()
        rules()
        val repos = fixture.repos()
        presenterTestOf(presenter(repos, FakeNavigator(ImportActivityScreen()), ride(profile = "MOUNTAIN"))) {
            val read = awaitFrom(picked()) { it.newDevices.profileOffer != null }
            val offer = assertNotNull(read.newDevices.profileOffer)
            assertEquals("MOUNTAIN", offer.name)
            assertEquals(setOf(tarmac, helmet), offer.options.map { it.uri }.toSet())
            read.newDevices.eventSink(NewDeviceEvent.LinkProfile(tarmac))
            awaitUntil { it.newDevices.profileOffer?.chosen == tarmac }.eventSink(ImportActivityEvent.Save(null))
            awaitUntil { fixture.putRecords.isNotEmpty() }
            assertEquals(listOf(GearProfileLink("MOUNTAIN", tarmac)), savedRules.profiles)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun notGearIsNeverOfferedAgainAndLaterIs() = runTest {
        storeBikes()
        rules()
        val repos = fixture.repos()
        presenterTestOf(presenter(repos, FakeNavigator(ImportActivityScreen()))) {
            val read = awaitFrom(picked()) { it.newDevices.cards.size == 3 }
            read.newDevices.eventSink(NewDeviceEvent.Decide(read.newDevices.cards[0].id, NewDeviceDecision.NotGear))
            awaitUntil { it.newDevices.cards[0].decision == NewDeviceDecision.NotGear }.eventSink(ImportActivityEvent.Save(null))
            awaitUntil { fixture.putRecords.isNotEmpty() }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("3510682071"), savedRules.ignored?.map { it.serialNumber })
        assertNull(savedRules.sensors, "nothing was linked: the strap and the power meter were left for later")

        // The next import from the same Edge.
        val again = fixture.repos()
        presenterTestOf(presenter(again, FakeNavigator(ImportActivityScreen()))) {
            val cards = awaitFrom(picked()) { it.newDevices.cards.size == 2 }.newDevices.cards
            assertEquals(listOf("HRM 200", "1052"), cards.map { it.model }, "not the ignored Edge")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun thePowerMeterIsAddedMountedOnTheTarmacAndInRaceKit() = runTest {
        storeBikes()
        rules()
        fixture.gearCollections["3race"] =
            AtJson.encodeToJsonElement(GearCollectionRecord.serializer(), GearCollectionRecord("Race kit", created)) as JsonObject
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf(presenter(repos, navigator)) {
            val read = awaitFrom(picked()) { it.newDevices.cards.size == 3 && it.newDevices.collections.isNotEmpty() }
            val meter = read.newDevices.cards.single { it.model == "1052" }
            assertEquals(tarmac, meter.mountOn, "a component goes on the bike picked for the ride")
            read.newDevices.eventSink(NewDeviceEvent.Decide(meter.id, NewDeviceDecision.Add))
            read.newDevices.eventSink(NewDeviceEvent.ToggleCollection(meter.id, "3race"))
            meter.name.setTextAndPlaceCursorAtEnd("SRAM Power meter")
            awaitUntil { s -> s.newDevices.cards.single { it.id == meter.id }.collections.isNotEmpty() }
                .eventSink(ImportActivityEvent.Save(null))
            assertTrue(navigator.awaitNextScreen() is ActivityDetailScreen, "nothing earlier to offer, so it leaves")
            cancelAndIgnoreRemainingEvents()
        }
        val (rkey, item) =
            fixture.gear.getValue(Visibility.SELF).entries.single().let { (rkey, json) ->
                rkey to AtJson.decodeFromJsonElement(GearItemRecord.serializer(), json)
            }
        assertEquals("SRAM Power meter", item.name)
        assertEquals(GearType.COMPONENT to "powerMeter", item.type to item.kind)
        assertEquals("SRAM", item.brand)
        assertEquals(listOf(GearMount(tarmac, start)), item.mounts, "mounted on the Tarmac from the ride's start")
        val race = AtJson.decodeFromJsonElement(GearCollectionRecord.serializer(), fixture.gearCollections.getValue("3race"))
        assertEquals(listOf(gearUri(rkey)), race.items)
        val link = savedRules.sensors.orEmpty().single()
        assertTrue(link.key.matches(meterKey), "$link")
        assertEquals("1052", link.key.model, "the model names it on the gear page")
        assertEquals(gearUri(rkey), link.gear)
        assertEquals(listOf(tarmac, helmet), publishedGear(), "the ride lists the Tarmac the meter is on")
        assertTrue(fixture.putRecords.none { "3236233426" in it.toString() }, "no serial is in the repo")
    }

    @Test
    fun aRenamedStrapIsAddedAndListedByTheRun() = runTest {
        storeBikes()
        rules()
        val repos = fixture.repos()
        presenterTestOf(presenter(repos, FakeNavigator(ImportActivityScreen()))) {
            val read = awaitFrom(picked()) { it.newDevices.cards.size == 3 }
            val strap = read.newDevices.cards.single { it.model == "HRM 200" }
            assertNull(strap.mountOn, "a strap isn't mounted on the bike")
            read.newDevices.eventSink(NewDeviceEvent.Decide(strap.id, NewDeviceDecision.Add))
            strap.name.setTextAndPlaceCursorAtEnd("Chest strap")
            awaitUntil { s -> s.newDevices.cards.single { it.id == strap.id }.decision == NewDeviceDecision.Add }
                .eventSink(ImportActivityEvent.Save(null))
            awaitUntil { fixture.putRecords.isNotEmpty() }
            cancelAndIgnoreRemainingEvents()
        }
        val (rkey, item) = fixture.gear.getValue(Visibility.SELF).entries.single()
        assertEquals("Chest strap", AtJson.decodeFromJsonElement(GearItemRecord.serializer(), item).name)
        assertEquals(GearType.SENSOR, AtJson.decodeFromJsonElement(GearItemRecord.serializer(), item).type)
        assertEquals(listOf(strapKey), savedRules.sensors?.map { it.key })
        assertEquals(listOf(gearUri(rkey), tarmac, helmet), publishedGear(), "the linked strap first, then the defaults")
    }

    @Test
    fun aGearFailureKeepsThePreviewAndPublishesNothing() = runTest {
        storeBikes()
        rules()
        fixture.failSpacePutsOf = setOf(Nsid.GEAR_ITEM)
        val repos = fixture.repos()
        presenterTestOf(presenter(repos, FakeNavigator(ImportActivityScreen()))) {
            val read = awaitFrom(picked()) { it.newDevices.cards.size == 3 }
            read.newDevices.eventSink(NewDeviceEvent.Decide(read.newDevices.cards[2].id, NewDeviceDecision.Add))
            awaitUntil { it.newDevices.cards[2].decision == NewDeviceDecision.Add }.eventSink(ImportActivityEvent.Save(null))
            val failed = awaitUntil { it.error != null && !it.saving }
            assertEquals(UiError.Kind.SaveNewGear, failed.error?.kind)
            assertNotNull(failed.preview, "the preview stays")
            assertTrue(fixture.putRecords.none { it["\$type"]?.jsonPrimitive?.content == Nsid.FEED_ACTIVITY }, "nothing published")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aLinkFailurePublishesAndSaysTheLinksWerentSaved() = runTest {
        storeBikes()
        rules()
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf(presenter(repos, navigator)) {
            val read = awaitFrom(picked()) { it.newDevices.cards.size == 3 }
            fixture.failSpacePutsOf = setOf(Nsid.GEAR_AUTO_ASSIGN)
            read.newDevices.eventSink(NewDeviceEvent.Decide(read.newDevices.cards[0].id, NewDeviceDecision.NotGear))
            awaitUntil { it.newDevices.cards[0].decision == NewDeviceDecision.NotGear }.eventSink(ImportActivityEvent.Save(null))
            val after = awaitUntil { it.afterImport != null }
            assertEquals(true, after.afterImport?.linksNotSaved)
            assertTrue(fixture.putRecords.any { it["\$type"]?.jsonPrimitive?.content == Nsid.FEED_ACTIVITY }, "published")
            after.eventSink(ImportActivityEvent.After.Done)
            assertTrue(navigator.awaitNextScreen() is ActivityDetailScreen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aNewLinkCanBeAppliedToEarlierImportsOrNot() = runTest {
        for (accept in listOf(false, true)) {
            val fixture = SessionTestFixture()
            val test = ImportSensorGearTestWith(fixture)
            test.storeEarlierRides(3)
            val repos = fixture.repos()
            val navigator = FakeNavigator(ImportActivityScreen())
            presenterTestOf(test.presenter(repos, navigator)) {
                awaitItem().eventSink(ImportActivityEvent.FilePicked("ride.fit", bytes))
                val read = awaitUntil { it.preview != null && it.newDevices.cards.size == 3 }
                val meter = read.newDevices.cards.single { it.model == "1052" }
                read.newDevices.eventSink(NewDeviceEvent.Decide(meter.id, NewDeviceDecision.Add))
                awaitUntil { s -> s.newDevices.cards.single { it.id == meter.id }.decision == NewDeviceDecision.Add }
                    .eventSink(ImportActivityEvent.Save(null))
                val offered = awaitUntil { it.afterImport?.pastImports != null }
                val offer = offered.afterImport?.pastImports!!
                assertEquals(3, offer.count, "three earlier rides name the meter")
                assertEquals(listOf("Tarmac"), offer.gear, "the bike the meter is on")
                val putsBefore = fixture.spacePuts.size + fixture.putRecords.size
                if (accept) {
                    offered.eventSink(ImportActivityEvent.After.ApplyToPast)
                    assertTrue(navigator.awaitNextScreen() is ActivityDetailScreen, "it leaves once they're done")
                    assertTrue(fixture.spacePuts.size + fixture.putRecords.size > putsBefore, "the earlier rides were rewritten")
                } else {
                    offered.eventSink(ImportActivityEvent.After.Done)
                    assertTrue(navigator.awaitNextScreen() is ActivityDetailScreen)
                    assertEquals(putsBefore, fixture.spacePuts.size + fixture.putRecords.size, "declining changes nothing")
                }
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun anOlderSessionStillMatchesButOffersNothing() = runTest {
        storeBikes()
        storeGear("3crank", GearItemRecord("Crank", GearType.COMPONENT, created, mounts = listOf(GearMount(stumpjumper, created))))
        rules(GearSensorLink(meterKey, gearUri("3crank")))
        // Signed in before the space declaration listed gear.autoAssign: links can be read but not written.
        fixture.scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}"
        val repos = fixture.repos()
        presenterTestOf(presenter(repos, FakeNavigator(ImportActivityScreen()), ride(profile = "MOUNTAIN"))) {
            val read = awaitFrom(picked()) { it.gear?.selected?.firstOrNull() == stumpjumper }
            assertEquals(listOf(stumpjumper, helmet), read.gear?.selected?.toList())
            assertEquals(emptyList(), read.newDevices.cards.toList())
            assertNull(read.newDevices.profileOffer)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aLogbookOfFiveDivesOffersItsDiveComputerOnce() = runTest {
        rules()
        val repos = fixture.repos()
        val logbook =
            ByteString(
                """<uddf version="3.2.0">
                  <diver><owner id="me"><equipment><divecomputer id="dc1">
                    <name>My Perdix</name><manufacturer id="sw"><name>Shearwater</name></manufacturer>
                    <model>Perdix AI</model><serialnumber>A1B2C3</serialnumber>
                  </divecomputer></equipment></owner></diver>
                  <profiledata><repetitiongroup id="rg">
                    ${(1..5).joinToString("\n") { n -> logbookDive(n, "${8 + n}:00") }}
                  </repetitiongroup></profiledata>
                </uddf>"""
                    .encodeToByteArray()
            )
        presenterTestOf(presenter(repos, FakeNavigator(ImportActivityScreen()))) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("logbook.uddf", logbook))
            val read = awaitUntil { it.dives?.dives?.size == 5 && it.newDevices.cards.isNotEmpty() }
            val card = read.newDevices.cards.single()
            assertTrue(card.recorder)
            assertEquals("B2C3", card.serialTail)
            assertEquals("Shearwater Perdix AI", card.name.text.toString(), "the maker once")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun logbookDive(number: Int, time: String) =
        """<dive id="d$number">
          <informationbeforedive><divenumber>$number</divenumber><datetime>2026-09-20T${time.padStart(5, '0')}:00</datetime></informationbeforedive>
          <samples>
            <waypoint><depth>0</depth><divetime>0</divetime></waypoint>
            <waypoint><depth>18</depth><divetime>1200</divetime></waypoint>
            <waypoint><depth>0</depth><divetime>2400</divetime></waypoint>
          </samples>
          <informationafterdive><greatestdepth>18</greatestdepth><diveduration>2400</diveduration></informationafterdive>
        </dive>"""

    /** A ride from an Edge 840 with a strap known only by its model and an SRAM power meter, recorded under [profile]. */
    private fun ride(profile: String? = null): DecodedActivity = edgeRide(start, profile)

    /** The same ride and helpers over another fixture. */
    private class ImportSensorGearTestWith(val fixture: SessionTestFixture) {
        private val created = Instant.parse("2026-01-01T00:00:00Z")
        private val start = Instant.parse("2026-09-20T07:00:00Z")

        fun storeEarlierRides(count: Int) {
            fun store(rkey: String, record: GearItemRecord) {
                fixture.gear.getOrPut("repo") { mutableMapOf() }[rkey] =
                    AtJson.encodeToJsonElement(GearItemRecord.serializer(), record) as JsonObject
            }
            store("3tarmac", GearItemRecord("Tarmac", GearType.BIKE, created))
            val tarmac = AtUri("at://${fixture.did.value}/${Nsid.GEAR_ITEM}/3tarmac")
            fixture.gearAutoAssign =
                AtJson.encodeToString(
                    GearAutoAssignRecord.serializer(),
                    GearAutoAssignRecord(created, sports = listOf(GearSportDefault(Sport.RIDE, listOf(tarmac)))),
                )
            // Imported before, but ridden after the meter went on the Tarmac.
            val earlier =
                ActivityRecord(
                    sport = Sport.RIDE,
                    startedAt = Instant.parse("2026-09-25T07:00:00Z"),
                    elapsedTime = 600,
                    createdAt = created,
                )
            fixture.activityRecord = AtJson.encodeToString(ActivityRecord.serializer(), earlier)
            val meter =
                ActivityDevice(
                    role = DeviceRole.SENSOR,
                    kind = SensorType.POWER,
                    manufacturer = "SRAM",
                    model = "1052",
                    serialNumber = "3236233426",
                )
            repeat(count) { i ->
                val rkey = "3earlier$i"
                val uri = AtUri.of(fixture.did, Nsid.FEED_ACTIVITY, rkey)
                fixture.servedActivities[uri.value] =
                    ActivityView(uri, Cid("bafyrec"), ProfileViewBasic(fixture.did, Handle("alice.test")), earlier, 0, 0, created)
                fixture.activityDevices[rkey] =
                    AtJson.encodeToJsonElement(
                        ActivityDevicesRecord.serializer(),
                        ActivityDevicesRecord(AtUri.of(fixture.did, Nsid.FEED_ACTIVITY, rkey), listOf(meter), created),
                    ) as JsonObject
            }
        }

        fun presenter(repos: SessionTestFixture.Repos, navigator: FakeNavigator) =
            @Composable {
                ImportActivityPresenter(
                    ImportActivityScreen(),
                    navigator,
                    repos.activities,
                    repos.manager,
                    ActivityFileReader { edgeRide(start, profile = null) },
                    repos.typeChoices,
                    repos.gearChoices,
                    repos.newDeviceChoices,
                    repos.profiles,
                    repos.feed,
                    repos.defaults,
                    repos.units,
                    repos.suggestions,
                )
            }
    }
}

/** A ride from an Edge 840 with a strap known only by its model and an SRAM power meter, recorded under [profile]. */
private fun edgeRide(start: Instant, profile: String?): DecodedActivity {
    val millis = { sec: Long -> (start + sec.seconds).toEpochMilliseconds() }
    return DecodedActivity(
        fileId = DecodedSource("activity", "garmin", "edge840", millis(0), serialNumber = "3510682071"),
        sessions =
            listOf(
                DecodedSummary(
                    "cycling",
                    null,
                    millis(0),
                    600.0,
                    600.0,
                    3000.0,
                    80.0,
                    null,
                    null,
                    140,
                    170,
                    avgPowerWatts = null,
                    avgCadence = null,
                    avgTemperatureCelsius = null,
                    sportProfileName = profile,
                )
            ),
        laps = emptyList(),
        records = (0L..10L).map { i -> DecodedSample(millis(i * 60), null, null, null, 140, 80, 180, null, null, null) },
        sensors =
            listOf(
                DecodedSensor(0, "local", null, "garmin", "edge840", null, null, serialNumber = 3_510_682_071L),
                DecodedSensor(1, "antplus", "heart_rate", "garmin", "hrm_200", null, null),
                DecodedSensor(4, "antplus", "bike_power", "sram", "1052", null, null, serialNumber = 3_236_233_426L),
            ),
    )
}
