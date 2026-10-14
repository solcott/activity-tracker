package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
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
import kotlinx.coroutines.flow.first
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
        presenterTestOf({ EditProfilePresenter(navigator, repos.profiles, repos.units, repos.auth) }) {
            val loaded = awaitUntil { it.loaded }
            assertEquals("Alice", loaded.displayName.text.toString())
            assertEquals("Runner", loaded.description.text.toString())

            loaded.displayName.setTextAndPlaceCursorAtEnd(" Alice A. ")
            loaded.description.clearText()
            loaded.eventSink(EditProfileEvent.Save)
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
        presenterTestOf({ EditProfilePresenter(navigator, repos.profiles, repos.units, repos.auth) }) {
            val loaded = awaitUntil { it.loaded }
            assertEquals("", loaded.displayName.text.toString())

            loaded.displayName.setTextAndPlaceCursorAtEnd("Alice")
            loaded.eventSink(EditProfileEvent.Save)
            val failed = awaitUntil { it.error != null && !it.saving }
            assertEquals(UiError.Kind.SaveProfile, failed.error?.kind)
            assertEquals("Alice", failed.displayName.text.toString())
            cancelAndIgnoreRemainingEvents()
        }
        navigator.assertPopIsEmpty()
    }

    @Test
    fun aSessionWithoutSpaceAccessAsksToSignInAgainAndStillEditsPublicDetails() = runTest {
        fixture.profileRecord = """{"displayName":"Alice","details":{"location":{"locality":"Portland"}}}"""
        fixture.scopeMissing = true
        val repos = fixture.repos()
        presenterTestOf({ EditProfilePresenter(FakeNavigator(EditProfileScreen), repos.profiles, repos.units, repos.auth) }) {
            val loaded = awaitUntil { it.loaded }
            assertEquals(UiError.Kind.SignInAgain, loaded.error?.kind)
            assertEquals("Portland", loaded.locality.text.toString())

            loaded.eventSink(EditProfileEvent.SignInAgain)
            repos.auth.session.first { it == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun savingWithoutSpaceAccessAsksToSignInAgain() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(EditProfileScreen)
        presenterTestOf({ EditProfilePresenter(navigator, repos.profiles, repos.units, repos.auth) }) {
            val loaded = awaitUntil { it.loaded }
            fixture.scopeMissing = true
            loaded.weight.setTextAndPlaceCursorAtEnd("60")
            loaded.eventSink(EditProfileEvent.VisibilityChanged(DetailGroup.Body, Visibility.MUTUALS))
            loaded.eventSink(EditProfileEvent.Save)
            val failed = awaitUntil { it.error != null && !it.saving }
            assertEquals(UiError.Kind.SignInAgain, failed.error?.kind)
            cancelAndIgnoreRemainingEvents()
        }
        navigator.assertPopIsEmpty()
    }

    @Test
    fun overLongNameIsInvalid() = runTest {
        val repos = fixture.repos()
        presenterTestOf({ EditProfilePresenter(FakeNavigator(EditProfileScreen), repos.profiles, repos.units, repos.auth) }) {
            val loaded = awaitUntil { it.loaded }
            loaded.displayName.setTextAndPlaceCursorAtEnd("a".repeat(EditProfileState.DISPLAY_NAME_MAX_LENGTH))
            assertTrue(loaded.valid)
            loaded.displayName.setTextAndPlaceCursorAtEnd("a".repeat(EditProfileState.DISPLAY_NAME_MAX_LENGTH + 1))
            assertFalse(loaded.valid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun hiddenDetailsGoToTheirTierSpaceAndWeightIsStoredInGrams() = runTest {
        val repos = fixture.repos(defaultUnits = UnitSystem.Imperial)
        val navigator = FakeNavigator(EditProfileScreen)
        presenterTestOf({ EditProfilePresenter(navigator, repos.profiles, repos.units, repos.auth) }) {
            val loaded = awaitUntil { it.loaded }
            assertEquals(Visibility.SELF, loaded.visibility.weight, "unset details default to Only me")

            loaded.weight.setTextAndPlaceCursorAtEnd("150")
            loaded.eventSink(EditProfileEvent.VisibilityChanged(DetailGroup.Body, Visibility.MUTUALS))
            loaded.locality.setTextAndPlaceCursorAtEnd("Portland")
            loaded.eventSink(EditProfileEvent.VisibilityChanged(DetailGroup.Location, Visibility.PUBLIC))
            loaded.eventSink(EditProfileEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }

        val record = fixture.putRecords.single()
        assertEquals("Portland", record["details"]?.jsonObject?.get("location")?.jsonObject?.get("locality")?.jsonPrimitive?.content)
        assertNull(record["details"]?.jsonObject?.get("weight"), "a Mutuals detail never goes in the public record")
        val hidden = fixture.privateDetailsPuts.last()
        assertEquals(68_039L, hidden["details"]?.jsonObject?.get("weight")?.jsonPrimitive?.long)
        assertEquals(Visibility.MUTUALS, hidden["visibility"]?.jsonPrimitive?.content)
    }

    @Test
    fun heightIsEnteredInFeetAndInchesAndDefaultsToOnlyMe() = runTest {
        fixture.privateDetails = """{"details":{"height":1803},"visibility":{}}"""
        val repos = fixture.repos(defaultUnits = UnitSystem.Imperial)
        val navigator = FakeNavigator(EditProfileScreen)
        presenterTestOf({ EditProfilePresenter(navigator, repos.profiles, repos.units, repos.auth) }) {
            val loaded = awaitUntil { it.loaded }
            assertEquals("5", loaded.height.text.toString())
            assertEquals("11", loaded.heightInches.text.toString())
            assertEquals(Visibility.SELF, loaded.visibility.weight)

            loaded.heightInches.setTextAndPlaceCursorAtEnd("13")
            assertTrue(loaded.valid, "13 in past 5 ft is still a height")
            loaded.height.setTextAndPlaceCursorAtEnd("tall")
            assertFalse(loaded.valid)
            loaded.height.setTextAndPlaceCursorAtEnd("6")
            loaded.heightInches.clearText()
            loaded.eventSink(EditProfileEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(fixture.putRecords.single()["details"], "an Only me height never goes in the public record")
        val hidden = fixture.privateDetailsPuts.last()
        assertEquals(1_829L, hidden["details"]?.jsonObject?.get("height")?.jsonPrimitive?.long)
        assertEquals(Visibility.SELF, hidden["visibility"]?.jsonPrimitive?.content)
    }

    @Test
    fun heightIsEnteredInCentimeters() = runTest {
        val repos = fixture.repos(defaultUnits = UnitSystem.Metric)
        val navigator = FakeNavigator(EditProfileScreen)
        presenterTestOf({ EditProfilePresenter(navigator, repos.profiles, repos.units, repos.auth) }) {
            val loaded = awaitUntil { it.loaded }
            loaded.height.setTextAndPlaceCursorAtEnd("400")
            assertFalse(loaded.valid)
            loaded.height.setTextAndPlaceCursorAtEnd("168")
            loaded.eventSink(EditProfileEvent.VisibilityChanged(DetailGroup.Body, Visibility.PUBLIC))
            loaded.eventSink(EditProfileEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1_680L, fixture.putRecords.single()["details"]?.jsonObject?.get("height")?.jsonPrimitive?.long)
    }

    @Test
    fun pickedAvatarShowsThenUploadsOnSave() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(EditProfileScreen)
        presenterTestOf({ EditProfilePresenter(navigator, repos.profiles, repos.units, repos.auth) }) {
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
        presenterTestOf({ EditProfilePresenter(FakeNavigator(EditProfileScreen), repos.profiles, repos.units, repos.auth) }) {
            val loaded = awaitUntil { it.loaded }
            loaded.weight.setTextAndPlaceCursorAtEnd("heavy")
            assertFalse(loaded.valid)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
