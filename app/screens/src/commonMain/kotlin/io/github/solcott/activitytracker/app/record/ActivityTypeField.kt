package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import kotlinx.collections.immutable.ImmutableList

/**
 * The activity type picker of the record, manual entry and import forms: the [selected] type and the quick row of [quickTypes]. A pick goes
 * to [eventSink], which the type's holder fills.
 */
@Immutable
data class ActivityTypeField(
    val selected: ActivityType,
    /** Recently tracked types, then the common ones. */
    val quickTypes: ImmutableList<ActivityType>,
    val eventSink: (ActivityTypeEvent) -> Unit = {},
)

sealed interface ActivityTypeEvent {
    data class Select(val type: ActivityType) : ActivityTypeEvent
}
