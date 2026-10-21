package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.settings.SettingsEvent
import io.github.solcott.activitytracker.app.settings.SettingsPresenter
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.app.settings.TrainingZonesScreen
import io.github.solcott.activitytracker.app.visibility.VisibilityEvent
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SettingsPresenterTest {
    private val fixture = SessionTestFixture()

    @Test
    fun choosingUnitsSavesThemToTheProfile() = runTest {
        val repos = fixture.repos(defaultUnits = UnitSystem.Metric)
        val moderation = fixture.moderation()
        presenterTestOf({
            SettingsPresenter(
                FakeNavigator(SettingsScreen),
                repos.units,
                repos.auth,
                repos.defaults,
                repos.privacy,
                moderation,
                repos.segmentPrefs,
            )
        }) {
            val initial = awaitItem()
            assertEquals(UnitSystem.Metric, initial.units)
            assertNull(initial.error)

            initial.eventSink(SettingsEvent.UnitsChanged(UnitSystem.Imperial))
            val saved = awaitUntil { it.units == UnitSystem.Imperial && !it.saving }
            assertNull(saved.error)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("imperial", fixture.putRecords.single()["units"]?.jsonPrimitive?.content)
    }

    @Test
    fun failedSaveRevertsAndShowsError() = runTest {
        fixture.failWrites = true
        val repos = fixture.repos(defaultUnits = UnitSystem.Metric)
        val moderation = fixture.moderation()
        presenterTestOf({
            SettingsPresenter(
                FakeNavigator(SettingsScreen),
                repos.units,
                repos.auth,
                repos.defaults,
                repos.privacy,
                moderation,
                repos.segmentPrefs,
            )
        }) {
            awaitItem().eventSink(SettingsEvent.UnitsChanged(UnitSystem.Imperial))
            // The revert comes through the units flow, which can land a frame after the error.
            val failed = awaitUntil { it.error != null && !it.saving && it.units == UnitSystem.Metric }
            assertEquals(UiError.Kind.SaveSettings, failed.error?.kind)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun backPops() = runTest {
        val repos = fixture.repos()
        val moderation = fixture.moderation()
        val navigator = FakeNavigator(SettingsScreen)
        presenterTestOf({
            SettingsPresenter(navigator, repos.units, repos.auth, repos.defaults, repos.privacy, moderation, repos.segmentPrefs)
        }) {
            awaitItem().eventSink(SettingsEvent.Back)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun zonesOpenTheZonesScreen() = runTest {
        val repos = fixture.repos()
        val moderation = fixture.moderation()
        val navigator = FakeNavigator(SettingsScreen)
        presenterTestOf({
            SettingsPresenter(navigator, repos.units, repos.auth, repos.defaults, repos.privacy, moderation, repos.segmentPrefs)
        }) {
            awaitItem().eventSink(SettingsEvent.OpenZones)
            assertEquals(TrainingZonesScreen, navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun defaultVisibilitiesSaveCappedAndReload() = runTest {
        val repos = fixture.repos()
        val moderation = fixture.moderation()
        presenterTestOf({
            SettingsPresenter(
                FakeNavigator(SettingsScreen),
                repos.units,
                repos.auth,
                repos.defaults,
                repos.privacy,
                moderation,
                repos.segmentPrefs,
            )
        }) {
            val loaded = awaitUntil { it.defaults != null }
            assertEquals(Visibility.PUBLIC, loaded.defaults?.activity, "never set: Public")
            assertEquals(DetailCategory.all.map { Visibility.PUBLIC }, loaded.defaults?.categories?.map { it.visibility })
            loaded.defaults!!.eventSink(VisibilityEvent.CategoryChanged(DetailCategory.HEART_RATE, Visibility.SELF))
            awaitUntil { it.savingDefaults }
            awaitUntil { !it.savingDefaults }.defaults!!.eventSink(VisibilityEvent.ActivityChanged(Visibility.MUTUALS))
            awaitUntil { it.savingDefaults }
            val saved = awaitUntil { !it.savingDefaults }
            assertEquals(Visibility.MUTUALS, saved.defaults?.activity)
            assertEquals(Visibility.SELF, saved.defaults?.tierOf(DetailCategory.HEART_RATE), "a narrower category stays")
            assertEquals(Visibility.MUTUALS, saved.defaults?.tierOf(DetailCategory.POWER), "a wider one is capped")
            assertEquals(persistentListOf(Visibility.MUTUALS, Visibility.SELF), saved.defaults?.categoryOptions)
            cancelAndIgnoreRemainingEvents()
        }
        val record = AtJson.parseToJsonElement(fixture.activityDefaults!!).jsonObject
        assertEquals(Visibility.MUTUALS, record.getValue("visibility").jsonPrimitive.content)
        assertEquals(Visibility.SELF, record.getValue("details").jsonObject.getValue("heartRate").jsonPrimitive.content)
        assertFalse("chartsVisibility" in record, "no charts default")

        // Opening Settings again, in a new session, reads them back.
        val again = fixture.repos()
        presenterTestOf({
            SettingsPresenter(
                FakeNavigator(SettingsScreen),
                again.units,
                again.auth,
                again.defaults,
                again.privacy,
                moderation,
                again.segmentPrefs,
            )
        }) {
            val reloaded = awaitUntil { it.defaults != null }.defaults
            assertEquals(Visibility.MUTUALS, reloaded?.activity)
            assertEquals(Visibility.SELF, reloaded?.tierOf(DetailCategory.HEART_RATE))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun narrowingTheActivityDefaultCapsTheCategories() = runTest {
        fixture.activityDefaults = """{"visibility":"followers","details":{"calories":"followers"}}"""
        val repos = fixture.repos()
        val moderation = fixture.moderation()
        presenterTestOf({
            SettingsPresenter(
                FakeNavigator(SettingsScreen),
                repos.units,
                repos.auth,
                repos.defaults,
                repos.privacy,
                moderation,
                repos.segmentPrefs,
            )
        }) {
            awaitUntil { it.defaults != null }.defaults!!.eventSink(VisibilityEvent.ActivityChanged(Visibility.SELF))
            awaitUntil { it.savingDefaults }
            val narrowed = awaitUntil { !it.savingDefaults }
            assertEquals(Visibility.SELF, narrowed.defaults?.activity)
            assertEquals(Visibility.SELF, narrowed.defaults?.tierOf(DetailCategory.CALORIES), "category default capped")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun segmentMatchingStartsOffAndTurnsOnAndOff() = runTest {
        val repos = fixture.repos()
        val moderation = fixture.moderation()
        presenterTestOf({
            SettingsPresenter(
                FakeNavigator(SettingsScreen),
                repos.units,
                repos.auth,
                repos.defaults,
                repos.privacy,
                moderation,
                repos.segmentPrefs,
            )
        }) {
            val loaded = awaitUntil { it.segmentMatching != null }
            assertEquals(false, loaded.segmentMatching, "never asked: off")
            assertTrue(loaded.canUseSegments)

            loaded.eventSink(SettingsEvent.SegmentMatchingChanged(true))
            awaitUntil { it.segmentMatching == true && !it.savingSegments }.eventSink(SettingsEvent.SegmentMatchingChanged(false))
            val off = awaitUntil { it.segmentMatching == false && !it.savingSegments }
            assertNull(off.error)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("false", AtJson.parseToJsonElement(fixture.segmentPrefs!!).jsonObject.getValue("matching").jsonPrimitive.content)
        assertNotNull(repos.segmentPrefs.saved.value, "each save starts the segment-track sweep for the session")
    }

    @Test
    fun theLeaderboardChoiceShowsOnlyWhileMatchingIsOnAndKeepsIt() = runTest {
        val repos = fixture.repos()
        val moderation = fixture.moderation()
        presenterTestOf({
            SettingsPresenter(
                FakeNavigator(SettingsScreen),
                repos.units,
                repos.auth,
                repos.defaults,
                repos.privacy,
                moderation,
                repos.segmentPrefs,
            )
        }) {
            val off = awaitUntil { it.segmentMatching != null && it.showOnLeaderboards != null }
            assertEquals(true, off.showOnLeaderboards, "shown by default")
            off.eventSink(SettingsEvent.LeaderboardsChanged(false))
            off.eventSink(SettingsEvent.SegmentMatchingChanged(true))
            val on = awaitUntil { it.segmentMatching == true && !it.savingSegments }
            val saved = AtJson.parseToJsonElement(fixture.segmentPrefs!!).jsonObject
            assertFalse("hideFromLeaderboards" in saved, "not offered while matching was off, so never saved")
            assertEquals(true, on.showOnLeaderboards)
            on.eventSink(SettingsEvent.LeaderboardsChanged(false))
            val hidden = awaitUntil { it.showOnLeaderboards == false && !it.savingLeaderboards }
            assertNull(hidden.error)
            val stored = AtJson.parseToJsonElement(fixture.segmentPrefs!!).jsonObject
            assertEquals("true", stored.getValue("hideFromLeaderboards").jsonPrimitive.content)
            assertEquals("true", stored.getValue("matching").jsonPrimitive.content, "matching kept")

            fixture.failWrites = true
            hidden.eventSink(SettingsEvent.LeaderboardsChanged(true))
            val failed = awaitUntil { it.error != null && !it.savingLeaderboards }
            assertEquals(UiError.Kind.SaveSettings, failed.error?.kind)
            assertEquals(false, failed.showOnLeaderboards, "the saved choice")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedSegmentMatchingSaveKeepsItOffAndShowsAnError() = runTest {
        val repos = fixture.repos()
        val moderation = fixture.moderation()
        presenterTestOf({
            SettingsPresenter(
                FakeNavigator(SettingsScreen),
                repos.units,
                repos.auth,
                repos.defaults,
                repos.privacy,
                moderation,
                repos.segmentPrefs,
            )
        }) {
            val loaded = awaitUntil { it.segmentMatching != null }
            fixture.failWrites = true
            loaded.eventSink(SettingsEvent.SegmentMatchingChanged(true))
            val failed = awaitUntil { it.error != null && !it.savingSegments }
            assertEquals(UiError.Kind.SaveSettings, failed.error?.kind)
            assertEquals(false, failed.segmentMatching)
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(fixture.segmentPrefs)
        assertNull(repos.segmentPrefs.saved.value, "no sweep after a failed save")
    }

    @Test
    fun spacesThatRefuseTheSessionLeaveSegmentMatchingUnknownAndUnsaved() = runTest {
        fixture.scopeMissing = true
        val repos = fixture.repos()
        val moderation = fixture.moderation()
        presenterTestOf({
            SettingsPresenter(
                FakeNavigator(SettingsScreen),
                repos.units,
                repos.auth,
                repos.defaults,
                repos.privacy,
                moderation,
                repos.segmentPrefs,
            )
        }) {
            val state = awaitItem()
            state.eventSink(SettingsEvent.SegmentMatchingChanged(true))
            val after = awaitUntil { !it.savingSegments }
            assertNull(after.segmentMatching)
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(fixture.segmentPrefs)
    }

    @Test
    fun aSegmentMatchingToggleSentTwiceSavesOnce() = runTest {
        val repos = fixture.repos()
        val moderation = fixture.moderation()
        presenterTestOf({
            SettingsPresenter(
                FakeNavigator(SettingsScreen),
                repos.units,
                repos.auth,
                repos.defaults,
                repos.privacy,
                moderation,
                repos.segmentPrefs,
            )
        }) {
            val loaded = awaitUntil { it.segmentMatching != null }
            loaded.eventSink(SettingsEvent.SegmentMatchingChanged(true))
            loaded.eventSink(SettingsEvent.SegmentMatchingChanged(true))
            awaitUntil { it.segmentMatching == true && !it.savingSegments }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, fixture.spacePuts.count { (_, record) -> "matching" in record })
    }
}
