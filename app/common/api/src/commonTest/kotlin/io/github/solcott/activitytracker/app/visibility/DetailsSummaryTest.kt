package io.github.solcott.activitytracker.app.visibility

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.toImmutableList

/** feed/detail-visibility: the overview a folded detail list shows on an activity form, tier first. */
class DetailsSummaryTest {
    private fun fields(activity: String, vararg categories: Pair<String, String>, runningForm: Boolean = true) =
        VisibilityFields(
            activity = activity,
            categories = categories.map { (category, tier) -> CategoryVisibility(category, tier) }.toImmutableList(),
            runningForm = runningForm,
        )

    private fun line(tier: String, what: AppText) =
        DetailsSummaryLine(LabelText.visibility(tier), AppText.named("detail_visibility_summary_tier", LabelText.visibility(tier), what))

    private fun names(vararg categories: String) = AppText.joined(categories.map(LabelText::detailCategory))!!

    private val every = AppText.named("detail_visibility_summary_all")
    private val rest = AppText.named("detail_visibility_summary_rest")

    @Test
    fun everyDetailAtTheActivitysTierIsSaidOnce() =
        assertEquals(
            listOf(line(Visibility.FOLLOWERS, every)),
            DetailsSummary.of(
                fields(Visibility.FOLLOWERS, DetailCategory.MAP to Visibility.FOLLOWERS, DetailCategory.POWER to Visibility.FOLLOWERS),
            ),
        )

    @Test
    fun narrowerDetailsAreNamedBeforeEverythingElse() =
        assertEquals(
            listOf(line(Visibility.SELF, names(DetailCategory.HEART_RATE, DetailCategory.MAP)), line(Visibility.PUBLIC, rest)),
            DetailsSummary.of(
                fields(
                    Visibility.PUBLIC,
                    DetailCategory.START_TIME to Visibility.PUBLIC,
                    DetailCategory.HEART_RATE to Visibility.SELF,
                    DetailCategory.MAP to Visibility.SELF,
                ),
            ),
        )

    @Test
    fun tiersRunWidestFirstAndNoneIsLeftOverWhenEveryDetailIsNarrower() =
        assertEquals(
            listOf(line(Visibility.FOLLOWERS, names(DetailCategory.MAP)), line(Visibility.SELF, names(DetailCategory.HEART_RATE))),
            DetailsSummary.of(
                fields(Visibility.PUBLIC, DetailCategory.HEART_RATE to Visibility.SELF, DetailCategory.MAP to Visibility.FOLLOWERS),
            ),
        )

    @Test
    fun cadenceIsNamedAsItsPickerNamesIt() =
        assertEquals(
            listOf(line(Visibility.MUTUALS, AppText.named("detail_category_cadence_only"))),
            DetailsSummary.of(fields(Visibility.PUBLIC, DetailCategory.CADENCE to Visibility.MUTUALS, runningForm = false)),
        )
}
