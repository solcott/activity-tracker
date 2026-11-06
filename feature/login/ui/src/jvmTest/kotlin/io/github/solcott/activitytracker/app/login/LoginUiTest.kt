package io.github.solcott.activitytracker.app.login

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test

/** The login screen's notes about how the last session ended. */
@OptIn(ExperimentalTestApi::class)
class LoginUiTest {
    @Test
    fun afterLeavingItSaysTheAccountIsUnchanged() = runComposeUiTest {
        setContent { AppPreview { LoginUi(LoginSamples.all.getValue("left")) } }
        onNodeWithText("You've left Activity Tracker", substring = true).assertExists()
        onNodeWithText("alice.test and its other apps are unchanged", substring = true).assertExists()
    }

    @Test
    fun signingInAgainSaysWhy() = runComposeUiTest {
        setContent { AppPreview { LoginUi(LoginSamples.all.getValue("signInAgain")) } }
        onNodeWithText("Sign in again to give Activity Tracker", substring = true).assertExists()
        onNodeWithText("You've left Activity Tracker", substring = true).assertDoesNotExist()
    }
}
