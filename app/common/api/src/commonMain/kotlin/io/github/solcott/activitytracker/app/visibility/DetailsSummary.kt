package io.github.solcott.activitytracker.app.visibility

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.lexicon.app.Visibility

/** What a folded detail list says on an activity form, chosen here for the shared and the native UI (dev/shared-display-text). */
object DetailsSummary {
    /**
     * The overview a folded detail list shows, the tier leading each line ([DetailsSummaryLine.tier], which the UIs set off): a line per
     * tier narrower than the activity naming its details, widest first, then the activity's own tier for "everything else" ("Followers:
     * Map", "Only me: Heart rate, Calories", "Public: everything else"), or for "every detail" when nothing is narrower.
     */
    fun of(fields: VisibilityFields): List<DetailsSummaryLine> {
        val tierOf = { category: CategoryVisibility -> Visibility.all[Visibility.rank(category.visibility)] }
        val activity = Visibility.known(fields.activity)
        val narrower =
            Visibility.atMost(activity).drop(1).mapNotNull { tier ->
                AppText.joined(fields.categories.filter { tierOf(it) == tier }.map { VisibilityTexts.categoryName(fields, it.category) })
                    ?.let { lineOf(tier, it) }
            }
        val atActivity = fields.categories.count { tierOf(it) == activity }
        val rest =
            when (atActivity) {
                0 -> null
                fields.categories.size -> lineOf(activity, AppText.named("detail_visibility_summary_all"))
                else -> lineOf(activity, AppText.named("detail_visibility_summary_rest"))
            }
        return narrower + listOfNotNull(rest)
    }

    private fun lineOf(tier: String, what: AppText) =
        DetailsSummaryLine(LabelText.visibility(tier), AppText.named("detail_visibility_summary_tier", LabelText.visibility(tier), what))

    /**
     * [of]'s line at [index]. Swift reads the lines through it: a class that only appears as a list's element gets no Swift type, and
     * naming it here gives it one.
     */
    fun line(fields: VisibilityFields, index: Int): DetailsSummaryLine = of(fields)[index]
}

/** One line of [DetailsSummary.of]: [text] in full, which starts with [tier], the part the UIs emphasise. */
@Immutable data class DetailsSummaryLine(val tier: AppText, val text: AppText)
