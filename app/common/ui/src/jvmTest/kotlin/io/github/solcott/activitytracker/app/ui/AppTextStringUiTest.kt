package io.github.solcott.activitytracker.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone

/** The shared UI reads a choice of text from its resources (dev/shared-display-text). */
@OptIn(ExperimentalTestApi::class)
class AppTextStringUiTest {
    private fun shows(text: AppText, expected: String, twentyFourHour: Boolean? = null) = runComposeUiTest {
        setContent {
            AppPreview(width = null) {
                CompositionLocalProvider(LocalHourClock provides twentyFourHour, LocalTimeZone provides TimeZone.UTC) {
                    Text(text.string())
                }
            }
        }
        onNodeWithText(expected).assertIsDisplayed()
    }

    /** 19:05 UTC on 10 November 2026. */
    private val evening = AppText.Time(1_794_337_500)

    @Test fun aTimeOnATwelveHourClock() = shows(evening, "7:05 PM", twentyFourHour = false)

    @Test fun aTimeOnATwentyFourHourClock() = shows(evening, "19:05", twentyFourHour = true)

    @Test
    fun aTimeInAString() =
        shows(AppText.named("leave_paused", evening), "Your server asked to slow down. Carrying on at 19:05.", twentyFourHour = true)

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
