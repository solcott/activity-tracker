package io.github.solcott.activitytracker.lexicon.app

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** An activity's [ActivityRecord.sport] and optional [ActivityRecord.subSport], which only mean something together. */
@Immutable
@Serializable
data class ActivityType(
    /** A [Sport] value. */
    val sport: String,
    /** A [SubSport] value, or null for the sport's generic form. */
    val subSport: String? = null,
)

/** This record's [ActivityType]. */
val ActivityRecord.activityType: ActivityType
    get() = ActivityType(sport, subSport)
