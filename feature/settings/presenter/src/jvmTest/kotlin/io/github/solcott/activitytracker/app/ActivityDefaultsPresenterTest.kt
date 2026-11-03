package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.settings.ActivityDefaultsEvent
import io.github.solcott.activitytracker.app.settings.ActivityDefaultsPresenter
import io.github.solcott.activitytracker.app.settings.ActivityDefaultsScreen
import io.github.solcott.activitytracker.app.visibility.VisibilityEvent
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ActivityDefaultsPresenterTest {
    private val fixture = SessionTestFixture()

    @Test
    fun backPops() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(ActivityDefaultsScreen)
        presenterTestOf({ ActivityDefaultsPresenter(navigator, repos.defaults) }) {
            awaitItem().eventSink(ActivityDefaultsEvent.Back)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun defaultVisibilitiesSaveCappedAndReload() = runTest {
        val repos = fixture.repos()
        presenterTestOf({ ActivityDefaultsPresenter(FakeNavigator(ActivityDefaultsScreen), repos.defaults) }) {
            val loaded = awaitUntil { it.defaults != null }
            assertEquals(Visibility.PUBLIC, loaded.defaults?.activity, "never set: Public")
            assertEquals(
                DetailCategory.all.map { if (it == DetailCategory.GEAR) Visibility.SELF else Visibility.PUBLIC },
                loaded.defaults?.categories?.map { it.visibility },
                "Gear never set starts at Only me",
            )
            loaded.defaults!!.eventSink(VisibilityEvent.CategoryChanged(DetailCategory.HEART_RATE, Visibility.SELF))
            // A save can finish before a frame shows it running, so each waits for its answer rather than for `saving`.
            awaitUntil { !it.saving && it.defaults?.tierOf(DetailCategory.HEART_RATE) == Visibility.SELF }
                .defaults!!
                .eventSink(VisibilityEvent.ActivityChanged(Visibility.MUTUALS))
            val saved = awaitUntil { !it.saving && it.defaults?.activity == Visibility.MUTUALS }
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

        // Opening the page again, in a new session, reads them back.
        val again = fixture.repos()
        presenterTestOf({ ActivityDefaultsPresenter(FakeNavigator(ActivityDefaultsScreen), again.defaults) }) {
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
        presenterTestOf({ ActivityDefaultsPresenter(FakeNavigator(ActivityDefaultsScreen), repos.defaults) }) {
            val defaults = awaitUntil { it.defaults != null }.defaults!!
            val save = CompletableDeferred<Unit>().also { fixture.gate = it }
            defaults.eventSink(VisibilityEvent.ActivityChanged(Visibility.SELF))
            awaitUntil { it.saving }
            save.complete(Unit)
            val narrowed = awaitUntil { !it.saving }
            assertEquals(Visibility.SELF, narrowed.defaults?.activity)
            assertEquals(Visibility.SELF, narrowed.defaults?.tierOf(DetailCategory.CALORIES), "category default capped")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedSaveKeepsTheEarlierChoiceAndShowsAnErrorOnThePage() = runTest {
        fixture.failSpacePutsOf = setOf(Nsid.ACTOR_ACTIVITY_DEFAULTS)
        val repos = fixture.repos()
        presenterTestOf({ ActivityDefaultsPresenter(FakeNavigator(ActivityDefaultsScreen), repos.defaults) }) {
            awaitUntil { it.defaults != null }.defaults!!.eventSink(VisibilityEvent.ActivityChanged(Visibility.SELF))
            val failed = awaitUntil { it.error != null && !it.saving }
            assertEquals(UiError.Kind.SaveSettings, failed.error?.kind)
            assertEquals(Visibility.PUBLIC, failed.defaults?.activity)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aChangeSentTwiceWhileSavingSavesOnce() = runTest {
        val repos = fixture.repos()
        presenterTestOf({ ActivityDefaultsPresenter(FakeNavigator(ActivityDefaultsScreen), repos.defaults) }) {
            val defaults = awaitUntil { it.defaults != null }.defaults!!
            val save = CompletableDeferred<Unit>().also { fixture.gate = it }
            defaults.eventSink(VisibilityEvent.ActivityChanged(Visibility.SELF))
            val saving = awaitUntil { it.saving }
            saving.defaults!!.eventSink(VisibilityEvent.ActivityChanged(Visibility.MUTUALS))
            save.complete(Unit)
            assertEquals(Visibility.SELF, awaitUntil { !it.saving }.defaults?.activity)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, fixture.spacePuts.count { (_, record) -> "visibility" in record && "details" in record })
    }
}
