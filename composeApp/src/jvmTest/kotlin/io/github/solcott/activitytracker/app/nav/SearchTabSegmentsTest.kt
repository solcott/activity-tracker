package io.github.solcott.activitytracker.app.nav

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.presenterOf
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.search.AthleteSearchPresenter
import io.github.solcott.activitytracker.app.search.AthleteSearchScreen
import io.github.solcott.activitytracker.app.search.AthleteSearchState
import io.github.solcott.activitytracker.app.search.AthleteSearchUi
import io.github.solcott.activitytracker.app.segment.SegmentBrowseScreen
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.profile.AthleteSearchRepository
import io.github.solcott.activitytracker.lexicon.Did
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * The Search tab's Segments mode (segments/segment-browse): the browse screen shows under the tab's switch, what it opens goes on the tab's
 * back stack, and coming back finds the same browse screen, its retained state kept. The browse and profile screens are stand-ins.
 */
@OptIn(ExperimentalTestApi::class)
class SearchTabSegmentsTest {
    private data class BrowseStub(val created: Int, val open: () -> Unit) : CircuitUiState

    private data object ProfileStub : CircuitUiState

    /** How many times a browse presenter's retained state was created afresh. */
    private var created = 0

    /** Never called: nothing is typed into athlete search. */
    private val http = HttpClient()

    @Test
    fun segmentsModeOpensOnTheTabAndKeepsItsStateComingBack() = runComposeUiTest {
        val athletes = runBlocking { AthleteSearchRepository(signedInTestSession(http, did = Did("did:plc:alice")).clients) }
        val circuit =
            Circuit.Builder()
                .addPresenter<AthleteSearchScreen, AthleteSearchState> { _, navigator, _ ->
                    presenterOf { AthleteSearchPresenter(navigator, athletes) }
                }
                .addUi<AthleteSearchScreen, AthleteSearchState> { state, modifier -> AthleteSearchUi(state, modifier) }
                .addPresenter<SegmentBrowseScreen, BrowseStub> { _, navigator, _ ->
                    presenterOf {
                        val id = rememberRetained { ++created }
                        BrowseStub(id) { navigator.goTo(ProfileScreen("did:plc:carol")) }
                    }
                }
                .addUi<SegmentBrowseScreen, BrowseStub> { state, modifier ->
                    Text("browse ${state.created}", modifier.clickable { state.open() })
                }
                .addPresenter<ProfileScreen, ProfileStub> { _, _, _ -> presenterOf { ProfileStub } }
                .addUi<ProfileScreen, ProfileStub> { _, modifier -> Text("profile", modifier) }
                .build()
        lateinit var tab: Navigator
        setContent {
            AppPreview {
                CircuitCompositionLocals(circuit) {
                    val backStack = rememberSaveableBackStack(root = AthleteSearchScreen)
                    tab = rememberCircuitNavigator(backStack = backStack, onRootPop = {})
                    NavigableCircuitContent(navigator = tab, navStack = backStack)
                }
            }
        }

        onNodeWithText("Segments").performClick()
        onNodeWithText("browse 1").performClick()
        onNodeWithText("profile").assertExists()
        assertEquals(ProfileScreen("did:plc:carol"), tab.peek(), "opened on the tab's own back stack")

        runOnIdle { tab.pop() }
        onNodeWithText("browse 1").assertExists()
        assertEquals(1, created, "the same browse screen, not a new one")
    }
}
