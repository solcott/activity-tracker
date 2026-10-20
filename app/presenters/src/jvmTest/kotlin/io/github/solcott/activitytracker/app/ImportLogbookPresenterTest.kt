package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.record.DuplicateAction
import io.github.solcott.activitytracker.app.record.DuplicateCheck
import io.github.solcott.activitytracker.app.record.ExistingActivityPreview
import io.github.solcott.activitytracker.app.record.ImportActivityEvent
import io.github.solcott.activitytracker.app.record.ImportActivityPresenter
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.app.record.PartOfDay
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.CombineCue
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
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
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf({
            ImportActivityPresenter(
                ImportActivityScreen(),
                navigator,
                repos.activities,
                repos.manager,
                ActivityFileReader { error("not FIT") },
                repos.typeChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
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
    fun offersWhatAnySelectedDiveHolds() = runTest {
        // Only the first dive has heart rate.
        val withHeartRate =
            ByteString(
                logbook
                    .toByteArray()
                    .decodeToString()
                    .replaceFirst("<waypoint><depth>18</depth>", "<waypoint><heartrate>120</heartrate><depth>18</depth>")
                    .encodeToByteArray()
            )
        val repos = fixture.repos()
        presenterTestOf({
            ImportActivityPresenter(
                ImportActivityScreen(),
                FakeNavigator(ImportActivityScreen()),
                repos.activities,
                repos.manager,
                ActivityFileReader { error("not FIT") },
                repos.typeChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("logbook.uddf", withHeartRate))
            val read = awaitUntil { it.dives != null && it.visibility.tierOf(DetailCategory.HEART_RATE) != null }
            assertTrue(read.visibility.tierOf(DetailCategory.DIVE) != null, "every dive has depths")

            read.eventSink(ImportActivityEvent.DiveToggled(0))
            val without = awaitUntil { it.dives?.selectedCount == 2 }
            assertEquals(null, without.visibility.tierOf(DetailCategory.HEART_RATE), "no selected dive has heart rate")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun divesToggleOneByOneAndAllAtOnce() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf({
            ImportActivityPresenter(
                ImportActivityScreen(),
                navigator,
                repos.activities,
                repos.manager,
                ActivityFileReader { error("not FIT") },
                repos.typeChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
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
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf({
            ImportActivityPresenter(
                ImportActivityScreen(),
                navigator,
                repos.activities,
                repos.manager,
                ActivityFileReader { error("not FIT") },
                repos.typeChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
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
    fun eachPickedDiveKeepsTheLogbookNamingItsEntry() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf({
            ImportActivityPresenter(
                ImportActivityScreen(),
                navigator,
                repos.activities,
                repos.manager,
                ActivityFileReader { error("not FIT") },
                repos.typeChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("logbook.uddf", logbook))
            val read = awaitUntil { it.dives != null }
            assertFalse(read.originalTooLarge)
            read.eventSink(ImportActivityEvent.DiveToggled(1))
            awaitUntil { it.dives?.selectedCount == 2 }.eventSink(ImportActivityEvent.SaveDives(persistentMapOf(2 to "Evening Dive")))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }

        val sources = fixture.spacePuts.filter { (_, put) -> put.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY_SOURCE }
        assertEquals(listOf(Visibility.SELF, Visibility.SELF), sources.map { it.first })
        assertEquals(listOf("0", "2"), sources.map { it.second.getValue("entry").jsonPrimitive.content })
        assertEquals(setOf("uddf"), sources.map { it.second.getValue("format").jsonPrimitive.content }.toSet())
        assertEquals(1, sources.map { it.second.getValue("file").toString() }.toSet().size, "both name the one stored logbook")
    }

    /** The import screen, reading every file as UDDF. */
    private fun presenter(
        repos: SessionTestFixture.Repos,
        navigator: FakeNavigator,
        screen: ImportActivityScreen = ImportActivityScreen(),
    ) =
        @Composable {
            ImportActivityPresenter(
                screen,
                navigator,
                repos.activities,
                repos.manager,
                ActivityFileReader { error("not FIT") },
                repos.typeChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
            )
        }

    private fun savedActivities() =
        fixture.putRkeys
            .zip(fixture.putRecords)
            .filter { (_, record) -> record.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY }
            .map { (rkey, record) ->
                AtUri.of(fixture.did, Nsid.FEED_ACTIVITY, rkey) to AtJson.decodeFromJsonElement(ActivityRecord.serializer(), record)
            }

    @Test
    fun aDiveAlreadyLoggedStartsSetToCombineAndCanBeIgnored() = runTest {
        val repos = fixture.repos()
        fixture.ownActivities = listOf(loggedDive("2026-09-20T10:00:00Z"))
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf(presenter(repos, navigator)) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("logbook.uddf", logbook))
            awaitUntil { it.dives != null }.eventSink(ImportActivityEvent.TimeZoneChanged("UTC"))
            val checked = awaitUntil { it.dives?.clock?.zone?.id == "UTC" && it.duplicateCheck == DuplicateCheck.Done }
            val batch = assertNotNull(checked.dives)
            val duplicate = assertNotNull(batch.dives[0].duplicate)
            assertEquals(ExistingActivityPreview("Blue Hole", "2026-09-20 10:00", "Garmin Descent Mk3"), duplicate.existing)
            assertEquals(DuplicateAction.Combine, duplicate.action, "Combine is the default")
            assertEquals(
                listOf(DuplicateAction.Ignore, DuplicateAction.ImportAnyway, DuplicateAction.Combine),
                duplicate.actions.toList(),
                "no merge, no group",
            )
            assertFalse(duplicate.stillUploading)
            assertEquals(listOf(true, true, true), batch.dives.map { it.selected })
            assertEquals(1 to 0, batch.existingDuplicates to batch.inFileDuplicates)
            assertTrue(batch.allSelected, "duplicates aside")
            assertTrue(batch.hasNonDuplicates)

            checked.eventSink(ImportActivityEvent.AllDivesSelected(false))
            val none = awaitUntil { it.dives?.selectedCount == 1 }
            assertEquals(true, none.dives?.dives?.get(0)?.selected, "Deselect all leaves duplicates as they are")
            none.eventSink(ImportActivityEvent.AllDivesSelected(true))
            val all = awaitUntil { it.dives?.selectedCount == 3 }

            all.eventSink(ImportActivityEvent.DiveToggled(0))
            val ignored = awaitUntil { it.dives?.selectedCount == 2 }
            assertEquals(DuplicateAction.Ignore, ignored.dives?.dives?.get(0)?.duplicate?.action)
            ignored.eventSink(ImportActivityEvent.DiveToggled(0))
            assertEquals(DuplicateAction.Combine, awaitUntil { it.dives?.selectedCount == 3 }.dives?.dives?.get(0)?.duplicate?.action)
            all.eventSink(ImportActivityEvent.DuplicateActionChanged(0, DuplicateAction.Ignore))
            awaitUntil { it.dives?.selectedCount == 2 }.eventSink(ImportActivityEvent.SaveDives(persistentMapOf()))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val saved = savedActivities().map { it.second }
        assertEquals(
            listOf("2026-09-20T14:30:00Z", "2026-09-20T18:45:00Z"),
            saved.map { it.startedAt.toString() },
            "the ignored dive isn't saved",
        )
        assertTrue(saved.none { it.combined != null }, "and the log is unchanged")
    }

    @Test
    fun aDiveImportedAnywayIsListedToCombineAfterSaving() = runTest {
        val repos = fixture.repos(suggestionClock = fixedClock("2026-09-21T00:00:00Z"))
        fixture.ownActivities = listOf(loggedDive("2026-09-20T10:00:00Z"))
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf(presenter(repos, navigator)) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("logbook.uddf", logbook))
            awaitUntil { it.dives != null }.eventSink(ImportActivityEvent.TimeZoneChanged("UTC"))
            val checked = awaitUntil { it.dives?.clock?.zone?.id == "UTC" && it.duplicateCheck == DuplicateCheck.Done }
            checked.eventSink(ImportActivityEvent.DuplicateActionChanged(0, DuplicateAction.ImportAnyway))
            awaitUntil { it.dives?.dives?.get(0)?.duplicate?.action == DuplicateAction.ImportAnyway }
                .eventSink(ImportActivityEvent.SaveDives(persistentMapOf()))

            val listed = awaitUntil { it.savedSuggestions != null }
            val suggestion = listed.savedSuggestions!!.single()
            assertEquals(CombineCue.SameActivity, suggestion.cue)
            assertEquals(AtUri.of(fixture.did, Nsid.FEED_ACTIVITY, "3klogged"), suggestion.other.uri)
            assertEquals(Instant.parse("2026-09-20T10:02:00Z").epochSeconds, suggestion.startEpochSeconds)

            listed.eventSink(ImportActivityEvent.Saved.Open(suggestion.uri))
            assertEquals(ActivityDetailScreen(suggestion.uri.value, suggestOnOpen = true), navigator.awaitNextScreen())
            listed.eventSink(ImportActivityEvent.Saved.Done)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aLogbookWithNothingToCombineLeavesAfterSaving() = runTest {
        val repos = fixture.repos(suggestionClock = fixedClock("2026-09-21T00:00:00Z"))
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf(presenter(repos, navigator)) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("logbook.uddf", logbook))
            awaitUntil { it.dives != null && it.duplicateCheck == DuplicateCheck.Done }
                .eventSink(ImportActivityEvent.SaveDives(persistentMapOf()))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun fixedClock(now: String) =
        object : Clock {
            override fun now() = Instant.parse(now)
        }

    @Test
    fun aDuplicateCombinesWithTheLoggedDive() = runTest {
        val repos = fixture.repos()
        val logged = loggedDive("2026-09-20T10:00:00Z")
        fixture.ownActivities = listOf(logged)
        fixture.activityRecord = AtJson.encodeToString(ActivityRecord.serializer(), logged.record)
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf(presenter(repos, navigator)) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("logbook.uddf", logbook("10:02")))
            awaitUntil { it.preview != null }.eventSink(ImportActivityEvent.TimeZoneChanged("UTC"))
            val checked = awaitUntil { it.preview?.clock?.zone?.id == "UTC" && it.duplicateCheck == DuplicateCheck.Done }
            assertEquals(DuplicateAction.Combine, checked.preview?.duplicate?.action)
            checked.eventSink(ImportActivityEvent.Save("Morning Dive"))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val (imported, combined) = savedActivities()
        assertEquals(Instant.parse("2026-09-20T10:02:00Z"), imported.second.startedAt, "the import is an activity of its own")
        assertNull(imported.second.recordingOf)
        assertEquals(setOf(logged.uri, imported.first), combined.second.combined?.members?.toSet())
        assertEquals("Blue Hole", combined.second.title, "it starts from the logged dive's title")
        assertEquals(Instant.parse("2026-09-20T10:00:00Z"), combined.second.startedAt)

        val source = fixture.spacePuts.single { (_, put) -> put.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY_SOURCE }
        assertEquals("logbook.uddf", source.second.getValue("fileName").jsonPrimitive.content)
        assertEquals("0", source.second.getValue("entry").jsonPrimitive.content, "a logbook of one dive still names its entry")
    }

    @Test
    fun importAnywaySavesTheDiveOnItsOwn() = runTest {
        val repos = fixture.repos()
        fixture.ownActivities = listOf(loggedDive("2026-09-20T10:00:00Z"))
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf(presenter(repos, navigator)) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("logbook.uddf", logbook("10:02")))
            awaitUntil { it.preview != null }.eventSink(ImportActivityEvent.TimeZoneChanged("UTC"))
            awaitUntil { it.preview?.clock?.zone?.id == "UTC" && it.duplicateCheck == DuplicateCheck.Done }
                .eventSink(ImportActivityEvent.DuplicateActionChanged(0, DuplicateAction.ImportAnyway))
            awaitUntil { it.preview?.duplicate?.action == DuplicateAction.ImportAnyway }.eventSink(ImportActivityEvent.Save("Morning Dive"))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val saved = savedActivities().single().second
        assertEquals(Instant.parse("2026-09-20T10:02:00Z"), saved.startedAt)
        assertNull(saved.combined)
        assertNull(saved.recordingOf)
    }

    @Test
    fun aDiveStillUploadingCantBeCombinedWithYet() = runTest {
        val repos = fixture.repos()
        fixture.failWrites = true
        repos.activities.publish(
            ActivityDraft(ActivityType(Sport.DIVE), "Blue Hole", null, Instant.parse("2026-09-20T10:00:00Z"), 2400.seconds)
        )
        fixture.ownActivities = emptyList()
        presenterTestOf(presenter(repos, FakeNavigator(ImportActivityScreen()))) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("logbook.uddf", logbook("10:02")))
            awaitUntil { it.preview != null }.eventSink(ImportActivityEvent.TimeZoneChanged("UTC"))
            val checked = awaitUntil { it.preview?.clock?.zone?.id == "UTC" && it.preview?.duplicate != null }
            val duplicate = assertNotNull(checked.preview?.duplicate)
            assertTrue(duplicate.stillUploading)
            assertEquals(DuplicateAction.Ignore, duplicate.action, "not Combine while the logged dive is unsent")

            checked.eventSink(ImportActivityEvent.DuplicateActionChanged(0, DuplicateAction.Combine))
            checked.eventSink(ImportActivityEvent.DuplicateActionChanged(0, DuplicateAction.ImportAnyway))
            assertEquals(
                DuplicateAction.ImportAnyway,
                awaitUntil { it.preview?.duplicate?.action != DuplicateAction.Ignore }.preview?.duplicate?.action,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aDiveTwiceInTheFileIsSavedOnceAsACombinedDive() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf(presenter(repos, navigator)) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("two-computers.uddf", logbook("10:02", "10:03")))
            val read = awaitUntil { it.dives != null && it.duplicateCheck == DuplicateCheck.Done }
            assertEquals(DuplicateAction.Combine, read.dives?.dives?.get(1)?.duplicate?.action)
            assertEquals(2, read.dives?.selectedCount)
            read.eventSink(ImportActivityEvent.SaveDives(persistentMapOf(1 to "Morning Dive")))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val (first, second, combined) = savedActivities()
        assertEquals(listOf(first.first, second.first), combined.second.combined?.members)
        assertEquals("Blue Hole", combined.second.title, "it starts from the first copy's title")
        assertNull(first.second.recordingOf)
        assertNull(second.second.recordingOf)
    }

    @Test
    fun aZoneChangeCanMakeADuplicate() = runTest {
        val repos = fixture.repos()
        // Logged at 10:00 UTC; the file's 10:02 clock read in Cancun (UTC-5) is 15:02 UTC: the same length, 5 hours off.
        fixture.ownActivities = listOf(loggedDive("2026-09-20T10:00:00Z"))
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf({
            ImportActivityPresenter(
                ImportActivityScreen(),
                navigator,
                repos.activities,
                repos.manager,
                ActivityFileReader { error("not FIT") },
                repos.typeChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
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
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf({
            ImportActivityPresenter(
                ImportActivityScreen(),
                navigator,
                repos.activities,
                repos.manager,
                ActivityFileReader { error("not FIT") },
                repos.typeChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("two-computers.uddf", logbook("10:02", "10:03", "14:30")))
            val read = awaitUntil { it.duplicateCheck == DuplicateCheck.Failed && it.dives != null }
            val batch = assertNotNull(read.dives)
            assertEquals(listOf(null, 0, null), batch.dives.map { it.duplicate?.inFileIndex })
            assertEquals(listOf(true, true, true), batch.dives.map { it.selected }, "the copy combines with the first")
            assertEquals(0 to 1, batch.existingDuplicates to batch.inFileDuplicates)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aLogbookOfOnlyDuplicatesHasNothingToSelectAll() = runTest {
        val repos = fixture.repos()
        fixture.ownActivities = listOf(loggedDive("2026-09-20T10:00:00Z"))
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf({
            ImportActivityPresenter(
                ImportActivityScreen(),
                navigator,
                repos.activities,
                repos.manager,
                ActivityFileReader { error("not FIT") },
                repos.typeChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
            )
        }) {
            // Both computers' copies of the logged dive: one matches the log, the other the log too.
            awaitItem().eventSink(ImportActivityEvent.FilePicked("two-computers.uddf", logbook("10:02", "10:03")))
            awaitUntil { it.dives != null }.eventSink(ImportActivityEvent.TimeZoneChanged("UTC"))
            val checked = awaitUntil { it.dives?.clock?.zone?.id == "UTC" && it.duplicateCheck == DuplicateCheck.Done }
            val batch = assertNotNull(checked.dives)
            assertEquals(2, batch.selectedCount, "both combine with the logged dive")
            assertEquals(false, batch.hasNonDuplicates, "so the screen offers no Select all")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
