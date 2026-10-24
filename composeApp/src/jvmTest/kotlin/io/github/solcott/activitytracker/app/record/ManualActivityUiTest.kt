package io.github.solcott.activitytracker.app.record

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import kotlin.test.Test

/** What manual entry says when the athlete can't choose who sees an activity (spaces/client-protocol). */
@OptIn(ExperimentalTestApi::class)
class ManualActivityUiTest {
    @Test
    fun aPdsWithoutSpacesSaysTheServerCantNotToSignInAgain() = runComposeUiTest {
        setContent { AppPreview { ManualActivityUi(ManualActivitySamples.all.getValue("spacesUnsupported")) } }
        onNodeWithText("Your server doesn't support private activities yet, so they're Public.").assertExists()
        onNodeWithText("Your server doesn't support private activities and details yet", substring = true).assertExists()
        onNodeWithText("Sign in again", substring = true).assertDoesNotExist()
    }

    @Test
    fun aSessionFromBeforeTheSpaceScopeIsAskedToSignInAgain() = runComposeUiTest {
        val state = ManualActivitySamples.all.getValue(SampleKind.LOADED).copy(visibility = VisibilityFields(canChoose = false))
        setContent { AppPreview { ManualActivityUi(state) } }
        onNodeWithText("Sign in again to choose who sees your activities", substring = true).assertExists()
    }
}
