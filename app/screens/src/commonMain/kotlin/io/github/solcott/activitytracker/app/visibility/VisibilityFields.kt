package io.github.solcott.activitytracker.app.visibility

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Who may see an activity and each of its detail categories, as a form shows them: [activity] and each of [categories] are `Visibility`
 * values, no category wider than the activity ([categoryOptions]). A session without access to the athlete's spaces can't choose
 * ([canChoose] false): it's Public, and signing in again lifts that. [approvesFollowers] is the athlete's follow policy, which the
 * Followers choice explains. A change goes to [eventSink], which the holder of the choice fills.
 */
@Immutable
data class VisibilityFields(
    val activity: String = Visibility.PUBLIC,
    /**
     * The categories offered, in `DetailCategory.all` order: those the activity has data in (a recording's, those it records), or every one
     * for the defaults.
     */
    val categories: ImmutableList<CategoryVisibility> = persistentListOf(),
    val canChoose: Boolean = true,
    val approvesFollowers: Boolean = false,
    /** Whether the cadence category holds running form too, as for the defaults; false for an activity with only cadence itself. */
    val runningForm: Boolean = true,
    val eventSink: (VisibilityEvent) -> Unit = {},
) {
    /** What a category may be shown to: the activity's visibility or narrower, widest first. */
    val categoryOptions: ImmutableList<String>
        get() = Visibility.atMost(activity).toImmutableList()
}

/** One detail category (a `DetailCategory` token) and who may see it (a `Visibility` value). */
@Immutable data class CategoryVisibility(val category: String, val visibility: String)

sealed interface VisibilityEvent {
    /** The activity's visibility; wider categories narrow with it. */
    data class ActivityChanged(val visibility: String) : VisibilityEvent

    /** One of [VisibilityFields.categories], within [VisibilityFields.categoryOptions]. */
    data class CategoryChanged(val category: String, val visibility: String) : VisibilityEvent
}
