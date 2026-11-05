package io.github.solcott.activitytracker.app.names

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test

/** The name sheet (segments/segment-names): what proposing shows, the edit warning, and an unchanged name refused. */
@OptIn(ExperimentalTestApi::class)
class ProposePlaceNameUiTest {
    @Test
    fun theProposeSheetTellsWhatProposingShows() = runComposeUiTest {
        setContent { AppPreview { ProposePlaceNameUi(ProposePlaceNameSamples.propose) } }
        onNodeWithText("Proposing shows others, under your name,", substring = true).assertExists()
        onNodeWithText("Send").assertExists()
    }

    @Test
    fun theEditSheetWarnsThatVotesAreCleared() = runComposeUiTest {
        setContent { AppPreview { ProposePlaceNameUi(ProposePlaceNameSamples.edit) } }
        onNodeWithText("Editing the name clears every vote it has.").assertExists()
        onNodeWithText("Save").assertExists()
    }

    @Test
    fun anUnchangedNameIsRefused() = runComposeUiTest {
        setContent { AppPreview { ProposePlaceNameUi(ProposePlaceNameSamples.invalid) } }
        onNodeWithText("Enter a new name of at most 100 characters.").assertExists()
    }
}
