package io.github.solcott.activitytracker.app

import app.cash.turbine.ReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.profile.EditProfileEvent
import io.github.solcott.activitytracker.app.profile.EditProfilePresenter
import io.github.solcott.activitytracker.app.profile.EditProfileScreen
import io.github.solcott.activitytracker.app.profile.EditProfileState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive

class EditProfilePresenterTest {
    private val fixture = SessionTestFixture()

    @Test
    fun savingKeepsOtherFieldsAndShowsOnTheProfileAtOnce() = runTest {
        fixture.profileRecord = """{"displayName":"Alice","description":"Runner","units":"imperial"}"""
        val repos = fixture.repos()
        val navigator = FakeNavigator(EditProfileScreen)
        presenterTestOf({ EditProfilePresenter(navigator, repos.profiles) }) {
            val loaded = awaitUntil { it.loaded }
            assertEquals("Alice", loaded.displayName)
            assertEquals("Runner", loaded.description)

            loaded.eventSink(EditProfileEvent.DisplayNameChanged(" Alice A. "))
            awaitItem().eventSink(EditProfileEvent.DescriptionChanged(""))
            awaitItem().eventSink(EditProfileEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }

        val record = fixture.putRecords.single()
        assertEquals("Alice A.", record["displayName"]?.jsonPrimitive?.content)
        assertNull(record["description"], "a blank bio clears the field")
        assertEquals("imperial", record["units"]?.jsonPrimitive?.content)

        // The fake AppView still serves the old profile; the repository lays the saved edit over it.
        val shown = repos.profiles.profile(fixture.did.value)
        assertEquals("Alice A.", shown.displayName)
        assertNull(shown.description)
    }

    @Test
    fun failedSaveStaysAndShowsError() = runTest {
        fixture.failWrites = true
        val repos = fixture.repos()
        val navigator = FakeNavigator(EditProfileScreen)
        presenterTestOf({ EditProfilePresenter(navigator, repos.profiles) }) {
            val loaded = awaitUntil { it.loaded }
            assertEquals("", loaded.displayName)

            loaded.eventSink(EditProfileEvent.DisplayNameChanged("Alice"))
            awaitItem().eventSink(EditProfileEvent.Save)
            val failed = awaitUntil { it.error != null && !it.saving }
            assertEquals(UiError.Kind.SaveProfile, failed.error?.kind)
            assertEquals("Alice", failed.displayName)
            cancelAndIgnoreRemainingEvents()
        }
        navigator.assertPopIsEmpty()
    }

    @Test
    fun overLongNameIsInvalid() = runTest {
        val repos = fixture.repos()
        presenterTestOf({ EditProfilePresenter(FakeNavigator(EditProfileScreen), repos.profiles) }) {
            awaitUntil { it.loaded }.eventSink(EditProfileEvent.DisplayNameChanged("a".repeat(EditProfileState.DISPLAY_NAME_MAX_LENGTH)))
            val atLimit = awaitItem()
            assertTrue(atLimit.valid)
            atLimit.eventSink(EditProfileEvent.DisplayNameChanged("a".repeat(EditProfileState.DISPLAY_NAME_MAX_LENGTH + 1)))
            assertFalse(awaitItem().valid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<EditProfileState>.awaitUntil(predicate: (EditProfileState) -> Boolean): EditProfileState {
        var state = awaitItem()
        while (!predicate(state)) state = awaitItem()
        return state
    }
}
