package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.DecodedActivity
import io.github.solcott.activitytracker.activityfile.DecodedSample
import io.github.solcott.activitytracker.activityfile.DecodedSource
import io.github.solcott.activitytracker.activityfile.DecodedSummary
import io.github.solcott.activitytracker.app.record.ImportActivityEvent
import io.github.solcott.activitytracker.app.record.ImportActivityPresenter
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.GearAutoAssignRecord
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearSportDefault
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonObject

/** An import's gear (`gear/activity-gear`): it starts at the athlete's defaults for the file's type. */
class ImportGearTest {
    private val fixture = SessionTestFixture()
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    /** Just a FIT header: the reader picks the decoder by the ".FIT" signature at byte 8, and the fake decoder ignores the rest. */
    private val bytes = ByteString(14, 16, 0, 0, 0, 0, 0, 0, '.'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'T'.code.toByte())

    @Test
    fun aMountainBikeRideStartsWithItsSubSportsBikeAndTheRidesHelmet() = runTest {
        val created = Instant.parse("2026-10-20T12:00:00Z")
        fun storeGear(rkey: String, record: GearItemRecord) {
            fixture.gear.getOrPut("repo") { mutableMapOf() }[rkey] =
                AtJson.encodeToJsonElement(GearItemRecord.serializer(), record) as JsonObject
        }
        fun gearUri(rkey: String) = AtUri("at://${fixture.did.value}/${Nsid.GEAR_ITEM}/$rkey")
        storeGear("3tarmac", GearItemRecord("Tarmac", GearType.BIKE, created))
        storeGear("3stumpy", GearItemRecord("Stumpjumper", GearType.BIKE, created))
        storeGear("3helmet", GearItemRecord("Helmet", GearType.HELMET, created))
        fixture.gearAutoAssign =
            AtJson.encodeToString(
                GearAutoAssignRecord.serializer(),
                GearAutoAssignRecord(
                    created,
                    listOf(
                        GearSportDefault(Sport.RIDE, listOf(gearUri("3tarmac"), gearUri("3helmet"))),
                        GearSportDefault(Sport.RIDE, listOf(gearUri("3stumpy")), subSport = SubSport.MOUNTAIN),
                    ),
                ),
            )
        val repos = fixture.repos()
        val ride = mtbRide()
        presenterTestOf({
            ImportActivityPresenter(
                ImportActivityScreen(),
                FakeNavigator(ImportActivityScreen()),
                repos.activities,
                repos.manager,
                ActivityFileReader { ride },
                repos.typeChoices,
                repos.gearChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("ride.fit", bytes))
            val read = awaitUntil { it.preview != null && it.gear?.selected?.isNotEmpty() == true }
            assertEquals(listOf(gearUri("3stumpy"), gearUri("3helmet")), read.gear?.selected?.toList())
            val gearTier = read.visibility.categories.first { it.category == DetailCategory.GEAR }
            assertEquals(Visibility.SELF, gearTier.visibility, "Gear never set starts at Only me")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A mountain bike ride from an Edge. */
    private fun mtbRide(): DecodedActivity {
        val millis = { sec: Long -> (start + sec.seconds).toEpochMilliseconds() }
        return DecodedActivity(
            fileId = DecodedSource("activity", "garmin", "edge840", millis(0)),
            sessions =
                listOf(
                    DecodedSummary(
                        "cycling",
                        "mountain",
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
                    )
                ),
            laps = emptyList(),
            records = (0L..10L).map { i -> DecodedSample(millis(i * 60), null, null, null, 140, 80, 180, null, null, null) },
        )
    }
}
