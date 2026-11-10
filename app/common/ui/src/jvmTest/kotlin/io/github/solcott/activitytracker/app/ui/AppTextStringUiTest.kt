package io.github.solcott.activitytracker.app.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/** The shared UI reads a choice of text from its resources (dev/shared-display-text). */
@OptIn(ExperimentalTestApi::class)
class AppTextStringUiTest {
    private fun shows(text: AppText, expected: String) = runComposeUiTest {
        setContent { AppPreview(width = null) { Text(text.string()) } }
        onNodeWithText(expected).assertIsDisplayed()
    }

    @Test fun aNamedStringWithItsArgument() = shows(AppText.named("unit_distance_mi", AppText.written("3.00")), "3.00 mi")

    @Test
    fun argumentsThatAreNamedThemselves() =
        shows(
            AppText.named("error_with_reason", AppText.named("error_load_feed"), AppText.named("error_reason_unreachable")),
            "Couldn't load your feed. Couldn't reach the server.",
        )

    @Test fun aPluralForOne() = shows(AppText.Plural("like_count", 1, persistentListOf(AppText.written("1"))), "1 like")

    @Test fun aPluralForMany() = shows(AppText.Plural("like_count", 3, persistentListOf(AppText.written("3"))), "3 likes")

    @Test
    fun aMissingNameReadsAsItsFallback() = shows(AppText.Named("sport_quidditch", fallback = AppText.written("Quidditch")), "Quidditch")

    @Test fun aMissingNameWithoutOneReadsAsTheName() = shows(AppText.named("no_such_string"), "no_such_string")
}
