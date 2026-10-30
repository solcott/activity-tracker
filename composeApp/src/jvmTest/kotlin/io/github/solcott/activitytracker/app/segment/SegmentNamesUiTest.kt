package io.github.solcott.activitytracker.app.segment

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.preview.SessionPreviewSubUis
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import kotlin.test.Test
import kotlin.test.assertEquals

/** A segment's community names on its screen and the name sheet (segments/segment-names). */
@OptIn(ExperimentalTestApi::class)
class SegmentNamesUiTest {
    private val events = mutableListOf<SegmentEvent>()

    private fun show(state: SegmentState, checks: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent { AppPreview(subUis = SessionPreviewSubUis) { SegmentUi(state.copy(eventSink = { events += it })) } }
        checks()
    }

    @Test
    fun aRenamedSegmentShowsItsOriginalNameProposalsAndHistory() =
        show(SegmentSamples.renamed) {
            onNodeWithText("Wiggly Woods").assertExists()
            onNodeWithText("originally Trail 214 Connector").assertExists()
            onNodeWithText("Rooty Run").performScrollTo().assertExists()
            onNodeWithText("Propose a name").assertDoesNotExist()
            onNodeWithText("Name history").performScrollTo().performClick()
            onNodeWithText("Proposed by ", substring = true).assertExists()
        }

    @Test
    fun tappingTheChosenVoteTakesItBack() =
        show(SegmentSamples.renamed) {
            onNodeWithContentDescription("Vote for Rooty Run").performScrollTo().performClick()
            onNodeWithContentDescription("Vote against Rooty Run").performClick()
            val bobs = AtUri(checkNotNull(SegmentSamples.renamed.segment).nameProposals.first().uri.value)
            assertEquals(
                listOf(SegmentEvent.Vote(bobs, null), SegmentEvent.Vote(bobs, SegmentNameVoteDirection.DOWN)),
                events.filterIsInstance<SegmentEvent.Vote>(),
            )
        }

    @Test
    fun onlyTheViewersOwnProposalOffersEditAndDelete() =
        show(SegmentSamples.renamed) {
            assertEquals(1, onAllNodesWithContentDescription("Edit name").fetchSemanticsNodes().size)
            onNodeWithContentDescription("Delete proposal").performScrollTo().performClick()
            onNodeWithContentDescription("Vote for Rooty Ridge").assertDoesNotExist()
            assertEquals(SegmentEvent.DeleteProposal::class, events.last()::class)
        }

    @Test
    fun aSessionThatCantVoteSaysWhyAndOffersPropose() =
        show(SegmentSamples.votingUnavailable) {
            onNodeWithText("Propose a name").performScrollTo().assertExists()
            onNodeWithText("Voting needs a server that supports private data.").assertExists()
            onNodeWithContentDescription("Vote for Rooty Run").assertIsNotEnabled()
        }

    @Test
    fun theProposeSheetTellsWhatProposingShows() = runComposeUiTest {
        setContent { AppPreview(subUis = SessionPreviewSubUis) { ProposeSegmentNameUi(ProposeSegmentNameSamples.propose) } }
        onNodeWithText("Proposing shows others, under your name,", substring = true).assertExists()
        onNodeWithText("Send").assertExists()
    }

    @Test
    fun theEditSheetWarnsThatVotesAreCleared() = runComposeUiTest {
        setContent { AppPreview(subUis = SessionPreviewSubUis) { ProposeSegmentNameUi(ProposeSegmentNameSamples.edit) } }
        onNodeWithText("Editing the name clears every vote it has.").assertExists()
        onNodeWithText("Save").assertExists()
    }

    @Test
    fun anUnchangedNameIsRefused() = runComposeUiTest {
        setContent { AppPreview(subUis = SessionPreviewSubUis) { ProposeSegmentNameUi(ProposeSegmentNameSamples.invalid) } }
        onNodeWithText("Enter a new name of at most 100 characters.").assertExists()
    }
}
