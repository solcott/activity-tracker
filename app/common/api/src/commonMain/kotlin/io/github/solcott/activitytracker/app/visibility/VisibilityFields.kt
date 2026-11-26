package io.github.solcott.activitytracker.app.visibility

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Who may see an activity and each of its detail categories, as a form shows them: [activity] and each of [categories] are `Visibility`
 * values, no category wider than the activity ([categoryOptions]). A session without access to the athlete's spaces can't choose
 * ([canChoose] false): it's Public, and signing in again lifts that, unless the athlete's PDS doesn't host spaces at all
 * ([spacesUnsupported]). [approvesFollowers] is the athlete's follow policy, which the Followers choice explains. A change goes to
 * [eventSink], which the holder of the choice fills.
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
    val spacesUnsupported: Boolean = false,
    val approvesFollowers: Boolean = false,
    /** Whether the cadence category holds running form too, as for the defaults; false for an activity with only cadence itself. */
    val runningForm: Boolean = true,
    /**
     * Each offered category at the athlete's default for it (a `Visibility` value, not yet capped at [activity]), which "Use my defaults"
     * ([VisibilityEvent.UseDefaults]) applies; null where there are none to apply, as on the defaults themselves.
     */
    val defaults: ImmutableList<CategoryVisibility>? = null,
    val eventSink: (VisibilityEvent) -> Unit = {},
) {
    /** What the activity may be shown to: every visibility, widest first. */
    val activityOptions: ImmutableList<String>
        get() = ALL_VISIBILITIES

    /** What a category may be shown to: the activity's visibility or narrower, widest first. */
    val categoryOptions: ImmutableList<String>
        get() = Visibility.atMost(activity).toImmutableList()

    /**
     * Whether "Match the activity" ([VisibilityEvent.MatchActivity]) would change anything: some category is narrower than the activity.
     */
    val offersMatchActivity: Boolean
        get() = canChoose && categories.any { Visibility.known(it.visibility) != Visibility.known(activity) }

    /** Whether "Use my defaults" ([VisibilityEvent.UseDefaults]) would change anything: some category isn't at its default, capped. */
    val offersDefaults: Boolean
        get() {
            val defaults = defaults ?: return false
            val byCategory = defaults.associate { it.category to it.visibility }
            return canChoose &&
                categories.any { category ->
                    byCategory[category.category]?.let { Visibility.capped(Visibility.known(it), activity) != category.visibility } == true
                }
        }
}

private val ALL_VISIBILITIES: ImmutableList<String> = Visibility.all.toImmutableList()

/** One detail category (a `DetailCategory` token) and who may see it (a `Visibility` value). */
@Immutable data class CategoryVisibility(val category: String, val visibility: String)

sealed interface VisibilityEvent {
    /** The activity's visibility; wider categories narrow with it. */
    data class ActivityChanged(val visibility: String) : VisibilityEvent

    /** One of [VisibilityFields.categories], within [VisibilityFields.categoryOptions]. */
    data class CategoryChanged(val category: String, val visibility: String) : VisibilityEvent

    /** Every one of [VisibilityFields.categories] at the activity's visibility. */
    data object MatchActivity : VisibilityEvent

    /**
     * Every one of [VisibilityFields.categories] at its default ([VisibilityFields.defaults]), capped at the activity; the activity stays.
     */
    data object UseDefaults : VisibilityEvent
}
