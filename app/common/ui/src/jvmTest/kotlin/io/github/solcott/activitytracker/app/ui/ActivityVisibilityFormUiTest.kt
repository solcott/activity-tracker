package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.app.visibility.CategoryVisibility
import io.github.solcott.activitytracker.app.visibility.VisibilityEvent
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** feed/detail-visibility: an activity form folds its detail pickers under a summary until opened; the defaults keep them open. */
@OptIn(ExperimentalTestApi::class)
class ActivityVisibilityFormUiTest {
    private val fields =
        VisibilityFields(
            activity = Visibility.PUBLIC,
            categories =
                persistentListOf(
                    CategoryVisibility(DetailCategory.START_TIME, Visibility.PUBLIC),
                    CategoryVisibility(DetailCategory.HEART_RATE, Visibility.SELF),
                ),
        )

    @Test
    fun detailsStartFoldedUnderTheirSummaryAndOpenInPlace() = runComposeUiTest {
        setContent { AppPreview { ActivityVisibilityForm(fields) } }
        onNodeWithText("Public: everything else").assertExists()
        onNodeWithText("Only me: Heart rate").assertExists()
        onNodeWithText("Start time").assertDoesNotExist()

        onNodeWithText("Details", useUnmergedTree = true).performClick()
        onNodeWithText("Start time").assertExists()
        onNodeWithText("Heart rate").assertExists()
    }

    @Test
    fun theSummaryFollowsTheChoice() = runComposeUiTest {
        var shown by mutableStateOf(fields)
        setContent { AppPreview { ActivityVisibilityForm(shown) } }
        shown = fields.copy(categories = persistentListOf(CategoryVisibility(DetailCategory.HEART_RATE, Visibility.PUBLIC)))
        onNodeWithText("Public: every detail").assertExists()
    }

    @Test
    fun theDefaultsKeepEveryPickerOpen() = runComposeUiTest {
        setContent { AppPreview { DetailVisibilityPicker(fields) } }
        onNodeWithText("Start time").assertExists()
        onNodeWithText("Only me: Heart rate").assertDoesNotExist()
    }

    @Test
    fun theOpenedListOffersOnlyTheResetsThatWouldChangeSomething() = runComposeUiTest {
        val events = mutableListOf<VisibilityEvent>()
        val withDefaults =
            fields.copy(
                defaults =
                    persistentListOf(
                        CategoryVisibility(DetailCategory.START_TIME, Visibility.PUBLIC),
                        CategoryVisibility(DetailCategory.HEART_RATE, Visibility.SELF),
                    ),
                eventSink = { events += it },
            )
        setContent { AppPreview { ActivityVisibilityForm(withDefaults) } }
        onNodeWithText("Details", useUnmergedTree = true).performClick()
        // Heart rate is Only me, which is the default: only matching the activity would change anything.
        onNodeWithText("Use my defaults").assertDoesNotExist()
        onNodeWithText("Match the activity").performClick()
        assertEquals(listOf<VisibilityEvent>(VisibilityEvent.MatchActivity), events)
    }
}
