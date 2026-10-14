package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.app.record.DuplicateAction
import io.github.solcott.activitytracker.app.record.DuplicateCheck
import io.github.solcott.activitytracker.app.record.ExistingActivityPreview
import io.github.solcott.activitytracker.app.record.ImportActivityEvent
import io.github.solcott.activitytracker.app.record.ImportActivityPresenter
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.app.record.MergeGain
import io.github.solcott.activitytracker.app.record.PartOfDay
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.jsonPrimitive

/** Importing a logbook (UDDF here) of several dives: picking them, and the duplicates among them. */
class ImportLogbookPresenterTest {
    private val fixture = SessionTestFixture()

    /** A UDDF logbook of three dives on one day's clock (no zone): the first at a named site, the others untitled. */
    private val logbook = logbook("10:02", "14:30", "18:45")

    private fun logbook(vararg times: String) =
        ByteString(
            """<uddf version="3.2.0">
              <divesite><site id="s1"><name>Blue Hole</name></site></divesite>
              <gasdefinitions><mix id="ean32"><o2>0.32</o2></mix></gasdefinitions>
              <profiledata><repetitiongroup id="rg">
                ${times.mapIndexed { i, time -> logbookDive(i + 1, time, site = i == 0) }.joinToString("\n")}
              </repetitiongroup></profiledata>
            </uddf>"""
                .encodeToByteArray()
        )

    /** A dive Alice already logged, as the AppView serves it. */
    private fun loggedDive(start: String, title: String? = "Blue Hole") =
        ActivityView(
            uri = AtUri.of(fixture.did, Nsid.FEED_ACTIVITY, "3klogged"),
            cid = Cid("bafylogged"),
            author = ProfileViewBasic(fixture.did, Handle("alice.test")),
            record =
                ActivityRecord(
                    Sport.DIVE,
                    Instant.parse(start),
                    2400,
                    Instant.parse("2026-09-21T00:00:00Z"),
                    title = title,
                    device = "Garmin Descent Mk3",
                ),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse("2026-09-21T00:00:00Z"),
        )

    private fun logbookDive(number: Int, time: String, site: Boolean) =
        """<dive id="d$number">
          <informationbeforedive>${if (site) "<link ref=\"s1\"/>" else ""}<divenumber>$number</divenumber>
            <datetime>2026-09-20T$time:00</datetime></informationbeforedive>
          <samples>
            <waypoint><depth>0</depth><divetime>0</divetime><temperature>299.15</temperature><switchmix ref="ean32"/></waypoint>
            <waypoint><depth>18</depth><divetime>1200</divetime></waypoint>
            <waypoint><depth>0</depth><divetime>2400</divetime></waypoint>
          </samples>
          <informationafterdive><greatestdepth>18</greatestdepth><diveduration>2400</diveduration></informationafterdive>
        </dive>"""

    @Test
    fun aLogbookListsItsDivesAllSelected() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { error("not FIT") },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("logbook.uddf", logbook))
            val read = awaitUntil { it.dives != null }
            assertNull(read.preview, "a logbook isn't one activity")
            val batch = assertNotNull(read.dives)
            assertEquals("logbook.uddf", batch.fileName)
            assertEquals(listOf(1, 2, 3), batch.dives.map { it.diveNumber })
            assertEquals(listOf("Blue Hole", null, null), batch.dives.map { it.title })
            assertEquals(3, batch.selectedCount)
            assertTrue(batch.allSelected)
            assertEquals("2026-09-20 10:02", assertNotNull(batch.clock).localStart)
            assertEquals(PartOfDay.Afternoon, batch.dives[1].defaultTitle.partOfDay)
            assertEquals(ActivityType(Sport.DIVE, SubSport.SINGLE_GAS), batch.dives[1].defaultTitle.type)
            assertEquals(18.0, batch.dives[0].maxDepthMeters)
            assertEquals(2400.seconds, batch.dives[0].elapsed)
            // Later states (the defaults and recent types loading) may still arrive; they're not this test's concern.
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun divesToggleOneByOneAndAllAtOnce() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { error("not FIT") },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("logbook.uddf", logbook))
            val read = awaitUntil { it.dives != null }

            read.eventSink(ImportActivityEvent.DiveToggled(1))
            val one = awaitUntil { it.dives?.selectedCount == 2 }
            assertEquals(listOf(true, false, true), one.dives?.dives?.map { it.selected })
            assertEquals(false, one.dives?.allSelected)

            one.eventSink(ImportActivityEvent.AllDivesSelected(false))
            val none = awaitUntil { it.dives?.selectedCount == 0 }
            none.eventSink(ImportActivityEvent.SaveDives(persistentMapOf()))

            none.eventSink(ImportActivityEvent.AllDivesSelected(true))
            assertEquals(true, awaitUntil { it.dives?.selectedCount == 3 }.dives?.allSelected)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(fixture.putRecords.isEmpty(), "nothing selected, nothing saved")
    }

    @Test
    fun savingALogbookImportsTheSelectedDivesInThePickedZone() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { error("not FIT") },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("logbook.uddf", logbook))
            awaitUntil { it.dives != null }.eventSink(ImportActivityEvent.DiveToggled(1))
            awaitUntil { it.dives?.selectedCount == 2 }.eventSink(ImportActivityEvent.TimeZoneChanged("America/Cancun"))
            val placed = awaitUntil { it.dives?.clock?.zone?.id == "America/Cancun" }
            placed.eventSink(ImportActivityEvent.SaveDives(persistentMapOf(1 to "Afternoon Dive", 2 to "Evening Dive")))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }

        val saved = fixture.putRecords.filter { it.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY }
        assertEquals(listOf("Blue Hole", "Evening Dive"), saved.map { it.getValue("title").jsonPrimitive.content })
        assertEquals(
            listOf("2026-09-20T15:02:00Z", "2026-09-20T23:45:00Z"),
            saved.map { it.getValue("startedAt").jsonPrimitive.content },
            "each dive's clock placed in Cancun (UTC-5)",
        )
        assertEquals(listOf("dive", "dive"), saved.map { it.getValue("sport").jsonPrimitive.content })
    }

    @Test
    fun aDiveAlreadyLoggedStartsUncheckedWithAWarning() = runTest {
        val repos = fixture.repos()
        fixture.ownActivities = listOf(loggedDive("2026-09-20T10:00:00Z"))
        val navigator = FakeNavigator(ImportActivityScreen)
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { error("not FIT") },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("logbook.uddf", logbook))
            awaitUntil { it.dives != null }.eventSink(ImportActivityEvent.TimeZoneChanged("UTC"))
            val checked = awaitUntil { it.dives?.clock?.zone?.id == "UTC" && it.duplicateCheck == DuplicateCheck.Done }
            val batch = assertNotNull(checked.dives)
            val duplicate = assertNotNull(batch.dives[0].duplicate)
            assertEquals(ExistingActivityPreview("Blue Hole", "2026-09-20 10:00", "Garmin Descent Mk3"), duplicate.existing)
            assertEquals(DuplicateAction.Ignore, duplicate.action)
            assertEquals(listOf(false, true, true), batch.dives.map { it.selected })
            assertEquals(1 to 0, batch.existingDuplicates to batch.inFileDuplicates)
            assertTrue(batch.allSelected, "duplicates aside")
            assertTrue(batch.hasNonDuplicates)

            checked.eventSink(ImportActivityEvent.AllDivesSelected(false))
            val none = awaitUntil { it.dives?.selectedCount == 0 }
            none.eventSink(ImportActivityEvent.AllDivesSelected(true))
            val all = awaitUntil { it.dives?.selectedCount == 2 }
            assertEquals(false, all.dives?.dives?.get(0)?.selected, "Select all leaves duplicates out")

            all.eventSink(ImportActivityEvent.DiveToggled(0))
            val anyway = awaitUntil { it.dives?.selectedCount == 3 }
            assertEquals(DuplicateAction.ImportAnyway, anyway.dives?.dives?.get(0)?.duplicate?.action)
            anyway.eventSink(ImportActivityEvent.DuplicateActionChanged(0, DuplicateAction.Ignore))
            awaitUntil { it.dives?.selectedCount == 2 }.eventSink(ImportActivityEvent.SaveDives(persistentMapOf()))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val saved = fixture.putRecords.filter { it.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY }
        assertEquals(listOf("2026-09-20T14:30:00Z", "2026-09-20T18:45:00Z"), saved.map { it.getValue("startedAt").jsonPrimitive.content })
    }

    @Test
    fun aDuplicateMergesIntoTheLoggedDive() = runTest {
        val repos = fixture.repos()
        val logged = loggedDive("2026-09-20T10:00:00Z")
        fixture.ownActivities = listOf(logged)
        fixture.activityRecord = AtJson.encodeToString(ActivityRecord.serializer(), logged.record.copy(device = null))
        val navigator = FakeNavigator(ImportActivityScreen)
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { error("not FIT") },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("logbook.uddf", logbook("10:02")))
            awaitUntil { it.preview != null }.eventSink(ImportActivityEvent.TimeZoneChanged("UTC"))
            val checked = awaitUntil { it.preview?.clock?.zone?.id == "UTC" && it.duplicateCheck == DuplicateCheck.Done }
            val duplicate = assertNotNull(checked.preview?.duplicate)
            assertEquals(DuplicateAction.entries, duplicate.actions.toList(), "every action for a logged dive")

            checked.eventSink(ImportActivityEvent.DuplicateActionChanged(0, DuplicateAction.Merge))
            // The logged dive has no charts, so it takes the file's once that's known.
            val merging = awaitUntil { MergeGain.Charts in it.preview?.duplicate?.mergeGains.orEmpty() }
            merging.eventSink(ImportActivityEvent.Save("Morning Dive"))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val saved = fixture.putRecords.filter { it.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY }
        val merged = AtJson.decodeFromJsonElement(ActivityRecord.serializer(), saved.single())
        assertEquals("Blue Hole", merged.title, "the logged dive's title stays")
        assertEquals(Instant.parse("2026-09-20T10:00:00Z"), merged.startedAt)
        assertNull(merged.device, "the UDDF names no computer, so there's none to add")
        assertEquals(SubSport.SINGLE_GAS, merged.subSport)
    }

    @Test
    fun aMergeAddsNoChartsTheLoggedDiveAlreadyHas() = runTest {
        val repos = fixture.repos()
        fixture.ownActivities = listOf(loggedDive("2026-09-20T10:00:00Z"))
        fixture.activityStreams = ActivityStreamData(time = listOf(0, 2400), temperature = listOf(260, 255), depth = listOf(0, 0))
        presenterTestOf({
            ImportActivityPresenter(
                FakeNavigator(ImportActivityScreen),
                repos.activities,
                ActivityFileReader { error("not FIT") },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("logbook.uddf", logbook("10:02")))
            awaitUntil { it.preview != null }.eventSink(ImportActivityEvent.TimeZoneChanged("UTC"))
            val checked = awaitUntil { it.preview?.duplicate != null && it.duplicateCheck == DuplicateCheck.Done }
            // Its charts aren't looked up until the athlete picks Merge.
            assertTrue(MergeGain.AnyCharts in checked.preview?.duplicate?.mergeGains.orEmpty())

            checked.eventSink(ImportActivityEvent.DuplicateActionChanged(0, DuplicateAction.Merge))
            val loaded = awaitUntil {
                val duplicate = it.preview?.duplicate
                duplicate?.action == DuplicateAction.Merge && MergeGain.AnyCharts !in duplicate.mergeGains
            }
            assertTrue(MergeGain.Charts !in loaded.preview?.duplicate?.mergeGains.orEmpty(), "it has depth and temperature already")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aDuplicateGroupsWithTheLoggedDiveAsAnotherRecording() = runTest {
        val repos = fixture.repos()
        val logged = loggedDive("2026-09-20T10:00:00Z")
        fixture.ownActivities = listOf(logged)
        val navigator = FakeNavigator(ImportActivityScreen)
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { error("not FIT") },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("logbook.uddf", logbook("10:02")))
            awaitUntil { it.preview != null }.eventSink(ImportActivityEvent.TimeZoneChanged("UTC"))
            val checked = awaitUntil { it.preview?.clock?.zone?.id == "UTC" && it.duplicateCheck == DuplicateCheck.Done }
            checked.eventSink(ImportActivityEvent.DuplicateActionChanged(0, DuplicateAction.Group))
            awaitUntil { it.preview?.duplicate?.action == DuplicateAction.Group }.eventSink(ImportActivityEvent.Save("Morning Dive"))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val saved = fixture.putRecords.filter { it.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY }
        val recording = AtJson.decodeFromJsonElement(ActivityRecord.serializer(), saved.single())
        assertEquals(logged.uri, recording.recordingOf)
        assertEquals(Instant.parse("2026-09-20T10:02:00Z"), recording.startedAt, "a recording of its own, not a merge")
    }

    @Test
    fun aDiveTwiceInTheFileGroupsWithItsFirstRecording() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen)
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { error("not FIT") },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("two-computers.uddf", logbook("10:02", "10:03")))
            val read = awaitUntil { it.dives != null && it.duplicateCheck == DuplicateCheck.Done }
            assertEquals(
                listOf(DuplicateAction.Ignore, DuplicateAction.ImportAnyway, DuplicateAction.Group),
                read.dives?.dives?.get(1)?.duplicate?.actions?.toList(),
            )
            read.eventSink(ImportActivityEvent.DuplicateActionChanged(1, DuplicateAction.Group))
            awaitUntil { it.dives?.selectedCount == 2 }.eventSink(ImportActivityEvent.SaveDives(persistentMapOf(1 to "Morning Dive")))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val activities =
            fixture.putRkeys.zip(fixture.putRecords).filter { (_, record) ->
                record.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY
            }
        val (first, second) = activities.map { (rkey, record) -> rkey to AtJson.decodeFromJsonElement(ActivityRecord.serializer(), record) }
        assertEquals(null, first.second.recordingOf)
        assertEquals(
            AtUri.of(fixture.did, Nsid.FEED_ACTIVITY, first.first),
            second.second.recordingOf,
            "published after the dive it records",
        )
    }

    @Test
    fun aZoneChangeCanMakeADuplicate() = runTest {
        val repos = fixture.repos()
        // Logged at 10:00 UTC; the file's 10:02 clock read in Cancun (UTC-5) is 15:02 UTC: the same length, 5 hours off.
        fixture.ownActivities = listOf(loggedDive("2026-09-20T10:00:00Z"))
        val navigator = FakeNavigator(ImportActivityScreen)
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { error("not FIT") },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("logbook.uddf", logbook))
            awaitUntil { it.dives != null }.eventSink(ImportActivityEvent.TimeZoneChanged("America/Cancun"))
            val cancun = awaitUntil { it.dives?.clock?.zone?.id == "America/Cancun" && it.duplicateCheck == DuplicateCheck.Done }
            assertEquals(5, cancun.dives?.dives?.get(0)?.duplicate?.hoursApart, "the file's dive is 5 hours after the logged one")

            cancun.eventSink(ImportActivityEvent.TimeZoneChanged("UTC"))
            assertEquals(0, awaitUntil { it.dives?.clock?.zone?.id == "UTC" }.dives?.dives?.get(0)?.duplicate?.hoursApart)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aDiveTwiceInTheFileIsFlaggedEvenWhenTheLogCantBeRead() = runTest {
        val repos = fixture.repos()
        fixture.ownActivities = null
        val navigator = FakeNavigator(ImportActivityScreen)
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { error("not FIT") },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("two-computers.uddf", logbook("10:02", "10:03", "14:30")))
            val read = awaitUntil { it.duplicateCheck == DuplicateCheck.Failed && it.dives != null }
            val batch = assertNotNull(read.dives)
            assertEquals(listOf(null, 0, null), batch.dives.map { it.duplicate?.inFileIndex })
            assertEquals(listOf(true, false, true), batch.dives.map { it.selected })
            assertEquals(0 to 1, batch.existingDuplicates to batch.inFileDuplicates)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aLogbookOfOnlyDuplicatesHasNothingToSelectAll() = runTest {
        val repos = fixture.repos()
        fixture.ownActivities = listOf(loggedDive("2026-09-20T10:00:00Z"))
        val navigator = FakeNavigator(ImportActivityScreen)
        presenterTestOf({
            ImportActivityPresenter(
                navigator,
                repos.activities,
                ActivityFileReader { error("not FIT") },
                repos.recentTypes,
                repos.profiles,
                repos.feed,
                repos.defaults,
            )
        }) {
            // Both computers' copies of the logged dive: one matches the log, the other the log too.
            awaitItem().eventSink(ImportActivityEvent.FilePicked("two-computers.uddf", logbook("10:02", "10:03")))
            awaitUntil { it.dives != null }.eventSink(ImportActivityEvent.TimeZoneChanged("UTC"))
            val checked = awaitUntil { it.dives?.clock?.zone?.id == "UTC" && it.duplicateCheck == DuplicateCheck.Done }
            val batch = assertNotNull(checked.dives)
            assertEquals(0, batch.selectedCount)
            assertEquals(false, batch.hasNonDuplicates, "so the screen offers no Select all")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
