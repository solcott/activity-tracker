package io.github.solcott.activitytracker.app.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Account page (auth/account-page). */
@OptIn(ExperimentalTestApi::class)
class AccountUiTest {
    @Test
    fun showsTheAccountAndWhereItsManaged() = runComposeUiTest {
        setContent { AppPreview { AccountUi(AccountSamples.default) } }
        onNodeWithText("alice.test").assertExists()
        onNodeWithText("did:plc:4z5n2d7xkqvu3hy6wbjmlcfe").assertExists()
        onNodeWithText("pds.example.com").assertExists()
        onNodeWithText("Your sign-in is up to date.").assertExists()
        onNodeWithText("Sign in again").assertDoesNotExist()
        onNodeWithText("managed by pds.example.com", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun copyingTheDidConfirmsIt() = runComposeUiTest {
        setContent { AppPreview { AccountUi(AccountSamples.default) } }
        onNodeWithText("Copy").performClick()
        onNodeWithText("DID copied").assertExists()
    }

    @Test
    fun anOutdatedSignInListsWhatsMissingAndOffersSigningInAgain() = runComposeUiTest {
        val events = mutableListOf<AccountEvent>()
        setContent { AppPreview { AccountUi(AccountSamples.all.getValue("outdated").copy(eventSink = { events += it })) } }
        onNodeWithText("Likes and comments on private activities", substring = true).assertExists()
        onNodeWithText("Segments, leaderboards and grouping", substring = true).assertExists()
        onNodeWithText("Privacy zones", substring = true).assertDoesNotExist()
        onNodeWithText("Sign in again").performClick()
        assertEquals(AccountEvent.SignInAgain, events.last())
    }

    @Test
    fun aPdsWithoutSpacesOffersNoSignInAgain() = runComposeUiTest {
        setContent { AppPreview { AccountUi(AccountSamples.all.getValue("spacesUnsupported")) } }
        onNodeWithText("can't keep private data", substring = true).assertExists()
        onNodeWithText("Sign in again").assertDoesNotExist()
    }

    @Test
    fun signOutSendsItsEvent() = runComposeUiTest {
        val events = mutableListOf<AccountEvent>()
        setContent { AppPreview { AccountUi(AccountSamples.default.copy(eventSink = { events += it })) } }
        onNodeWithText("Sign out").performScrollTo().performClick()
        assertEquals(AccountEvent.SignOut, events.last())
    }

    @Test
    fun pendingWorkIsSpelledOutBeforeSigningOut() = runComposeUiTest {
        val events = mutableListOf<AccountEvent>()
        setContent { AppPreview { AccountUi(AccountSamples.all.getValue("pendingImport").copy(eventSink = { events += it })) } }
        onNodeWithText("12 changes haven't uploaded yet", substring = true).assertExists()
        onNodeWithText("An import is still saving", substring = true).assertExists()
        onNodeWithText("Sign out anyway").performClick()
        // The dialog dismisses itself before its action runs, so the confirmation carries the choice.
        assertEquals(listOf(AccountEvent.Dismiss, AccountEvent.Confirm(signInAgain = false)), events.takeLast(2))
    }

    @Test
    fun noExportOffersOne() = runComposeUiTest {
        val events = mutableListOf<AccountEvent>()
        setContent { AppPreview { AccountUi(AccountSamples.default.copy(eventSink = { events += it })) } }
        onNodeWithText("including private activities", substring = true).performScrollTo().assertExists()
        onNodeWithText("Export your data").performScrollTo().performClick()
        assertEquals(AccountEvent.Export, events.last())
    }

    @Test
    fun aRunningExportShowsHowFarItsGotAndCanBeCancelled() = runComposeUiTest {
        val events = mutableListOf<AccountEvent>()
        setContent { AppPreview { AccountUi(AccountSamples.all.getValue("exportRunning").copy(eventSink = { events += it })) } }
        onNodeWithText("Preparing activities: 120 of 400").performScrollTo().assertExists()
        onNodeWithText("Export your data").assertDoesNotExist()
        onNodeWithText("Cancel export").performScrollTo().performClick()
        assertEquals(AccountEvent.CancelExport, events.last())
    }

    @Test
    fun aReadyExportOffersSaveAndSaysWhatWasLeftOut() = runComposeUiTest {
        setContent { AppPreview { AccountUi(AccountSamples.all.getValue("exportReadyWithProblems")) } }
        onNodeWithText("Your export is ready.").performScrollTo().assertExists()
        onNodeWithText("1 item couldn't be read", substring = true).performScrollTo().assertExists()
        onNodeWithText("2 changes still on this device", substring = true).performScrollTo().assertExists()
        onNodeWithText("Your private data isn't included", substring = true).performScrollTo().assertExists()
        onNodeWithText("Save").performScrollTo().assertExists()
    }

    @Test
    fun aPausedExportSaysHowMuchRoomItNeeds() = runComposeUiTest {
        val events = mutableListOf<AccountEvent>()
        setContent { AppPreview { AccountUi(AccountSamples.all.getValue("exportPaused").copy(eventSink = { events += it })) } }
        onNodeWithText("Free up 1,200 MB", substring = true).performScrollTo().assertExists()
        onNodeWithText("Try again").performScrollTo().performClick()
        assertEquals(AccountEvent.Export, events.last())
    }

    @Test
    fun aSharedExportAsksBeforeDeletingTheAppsCopy() = runComposeUiTest {
        val events = mutableListOf<AccountEvent>()
        setContent { AppPreview { AccountUi(AccountSamples.all.getValue("exportSharedAsk").copy(eventSink = { events += it })) } }
        onNodeWithText("Did you keep the export?").assertExists()
        onNodeWithText("Delete the app's copy").performClick()
        assertEquals(AccountEvent.KeptShared(kept = true), events.last())
    }

    @Test
    fun anUnsavedExportIsMentionedBeforeSigningOut() = runComposeUiTest {
        setContent { AppPreview { AccountUi(AccountSamples.all.getValue("pendingExport")) } }
        onNodeWithText("Your data export hasn't been saved", substring = true).assertExists()
    }
}
