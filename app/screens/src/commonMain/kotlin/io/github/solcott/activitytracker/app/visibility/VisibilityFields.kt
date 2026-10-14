package io.github.solcott.activitytracker.app.visibility

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Who may see an activity and its charts, as a form shows them: [activity] and [charts] are `Visibility` values, the charts never wider
 * than the activity ([chartsOptions]). A session without access to the athlete's spaces can't choose ([canChoose] false): it's Public, and
 * signing in again lifts that. [approvesFollowers] is the athlete's follow policy, which the Followers choice explains.
 */
@Immutable
data class VisibilityFields(
    val activity: String = Visibility.PUBLIC,
    val charts: String = Visibility.PUBLIC,
    val canChoose: Boolean = true,
    val approvesFollowers: Boolean = false,
) {
    /** What the charts may be shown to: the activity's visibility or narrower, widest first. */
    val chartsOptions: ImmutableList<String>
        get() = Visibility.atMost(activity).toImmutableList()
}
