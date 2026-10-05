package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.profile.AvatarImage
import io.github.solcott.activitytracker.app.profile.EditProfileEvent
import io.github.solcott.activitytracker.app.profile.EditProfilePresenter
import io.github.solcott.activitytracker.app.profile.EditProfileScreen
import io.github.solcott.activitytracker.app.profile.EditProfileState
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class EditProfilePresenterTest {
    private val fixture = SessionTestFixture()

    @Test
    fun savingKeepsOtherFieldsAndShowsOnTheProfileAtOnce() = runTest {
        fixture.profileRecord = """{"displayName":"Alice","description":"Runner","units":"imperial"}"""
        val repos = fixture.repos()
        val navigator = FakeNavigator(EditProfileScreen)
        presenterTestOf({ EditProfilePresenter(navigator, repos.profiles, repos.units) }) {
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
        presenterTestOf({ EditProfilePresenter(navigator, repos.profiles, repos.units) }) {
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
        presenterTestOf({ EditProfilePresenter(FakeNavigator(EditProfileScreen), repos.profiles, repos.units) }) {
            awaitUntil { it.loaded }.eventSink(EditProfileEvent.DisplayNameChanged("a".repeat(EditProfileState.DISPLAY_NAME_MAX_LENGTH)))
            val atLimit = awaitItem()
            assertTrue(atLimit.valid)
            atLimit.eventSink(EditProfileEvent.DisplayNameChanged("a".repeat(EditProfileState.DISPLAY_NAME_MAX_LENGTH + 1)))
            assertFalse(awaitItem().valid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun hiddenDetailsGoToTheAppViewAndWeightIsStoredInGrams() = runTest {
        val repos = fixture.repos(defaultUnits = UnitSystem.Imperial)
        val navigator = FakeNavigator(EditProfileScreen)
        presenterTestOf({ EditProfilePresenter(navigator, repos.profiles, repos.units) }) {
            val loaded = awaitUntil { it.loaded }
            assertEquals(Visibility.SELF, loaded.visibility.weight, "unset details default to Only me")

            loaded.eventSink(EditProfileEvent.WeightChanged("150"))
            awaitItem().eventSink(EditProfileEvent.VisibilityChanged(DetailGroup.Weight, Visibility.MUTUALS))
            awaitItem().eventSink(EditProfileEvent.LocalityChanged("Portland"))
            awaitItem().eventSink(EditProfileEvent.VisibilityChanged(DetailGroup.Location, Visibility.PUBLIC))
            awaitItem().eventSink(EditProfileEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }

        val record = fixture.putRecords.single()
        assertEquals("Portland", record["details"]?.jsonObject?.get("location")?.jsonObject?.get("locality")?.jsonPrimitive?.content)
        assertNull(record["details"]?.jsonObject?.get("weight"), "a Mutuals detail never goes in the public record")
        val hidden = fixture.privateDetailsPuts.last()
        assertEquals(68_039L, hidden["details"]?.jsonObject?.get("weight")?.jsonPrimitive?.long)
        assertEquals(Visibility.MUTUALS, hidden["visibility"]?.jsonObject?.get("weight")?.jsonPrimitive?.content)
    }

    @Test
    fun pickedAvatarShowsThenUploadsOnSave() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(EditProfileScreen)
        presenterTestOf({ EditProfilePresenter(navigator, repos.profiles, repos.units) }) {
            val loaded = awaitUntil { it.loaded }
            assertNull(loaded.avatar)
            val jpeg = ByteString(1, 2, 3)
            loaded.eventSink(EditProfileEvent.AvatarPicked(jpeg))
            val picked = awaitItem()
            assertEquals(AvatarImage.Picked(jpeg), picked.avatar)
            picked.eventSink(EditProfileEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(
            "bafkavatar",
            fixture.putRecords.single()["avatar"]?.jsonObject?.get("ref")?.jsonObject?.get("\$link")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun invalidWeightBlocksSaving() = runTest {
        val repos = fixture.repos()
        presenterTestOf({ EditProfilePresenter(FakeNavigator(EditProfileScreen), repos.profiles, repos.units) }) {
            awaitUntil { it.loaded }.eventSink(EditProfileEvent.WeightChanged("heavy"))
            assertFalse(awaitItem().valid)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
