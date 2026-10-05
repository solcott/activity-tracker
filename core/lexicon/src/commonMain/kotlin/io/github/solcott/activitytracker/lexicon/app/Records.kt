package io.github.solcott.activitytracker.lexicon.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Known values of [ActivityRecord.sport]; the field is an open string. */
object Sport {
    const val RUN = "run"
    const val RIDE = "ride"
    const val WALK = "walk"
    const val HIKE = "hike"
    const val SWIM = "swim"
    const val SKI = "ski"
    const val OTHER = "other"

    val all = listOf(RUN, RIDE, WALK, HIKE, SWIM, SKI, OTHER)
}

/** `app.activitytracker.feed.activity`. Units are integers because lexicons have no floats. */
@Immutable
@Serializable
data class ActivityRecord(
    val sport: String,
    val startedAt: Instant,
    /** Seconds. */
    val elapsedTime: Long,
    val createdAt: Instant,
    val title: String? = null,
    val description: String? = null,
    /** Seconds. */
    val movingTime: Long? = null,
    /** Meters. */
    val distance: Long? = null,
    /** Meters. */
    val elevationGain: Long? = null,
    /** Millimeters per second. */
    val avgSpeed: Long? = null,
    /** Millimeters per second. */
    val maxSpeed: Long? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    /** Tenths of a degree Celsius; may be negative. */
    val avgTemperature: Long? = null,
    /** Simplified Google encoded polyline (precision 5). */
    val polyline: String? = null,
    /** Full GPX track blob. */
    val track: BlobRef? = null,
    val device: String? = null,
    @SerialName("\$type") val type: String = Nsid.FEED_ACTIVITY,
)

@Serializable
data class KudosRecord(
    val subject: StrongRef,
    val createdAt: Instant,
    @SerialName("\$type") val type: String = Nsid.FEED_KUDOS,
)

@Serializable
data class CommentRecord(
    val subject: StrongRef,
    val text: String,
    val createdAt: Instant,
    @SerialName("\$type") val type: String = Nsid.FEED_COMMENT,
)

@Serializable
data class FollowRecord(
    val subject: Did,
    val createdAt: Instant,
    @SerialName("\$type") val type: String = Nsid.GRAPH_FOLLOW,
)

/** `app.activitytracker.actor.profile`. Records are public, so [details] holds only the details the athlete made public. */
@Serializable
data class ProfileRecord(
    val displayName: String? = null,
    val description: String? = null,
    val avatar: BlobRef? = null,
    val details: ProfileDetails? = null,
    val units: String? = null,
    val sports: List<String> = emptyList(),
    val createdAt: Instant? = null,
    @SerialName("\$type") val type: String = Nsid.ACTOR_PROFILE,
) {
    companion object {
        const val RKEY = "self"
    }
}

/** `app.activitytracker.actor.defs#location` */
@Immutable
@Serializable
data class Location(
    /** City or town. */
    val locality: String? = null,
    /** State, province or similar. */
    val region: String? = null,
    /** ISO 3166-1 alpha-2 code, upper case. */
    val country: String? = null,
)

/** `app.activitytracker.actor.defs#details`. Who sees each one is set by a [DetailsVisibility]. */
@Immutable
@Serializable
data class ProfileDetails(
    val location: Location? = null,
    /** Calendar date as `YYYY-MM-DD`. */
    val birthDate: String? = null,
    /** A [Gender] value, or the athlete's own description. */
    val gender: String? = null,
    /** Grams (lexicons have no floats). */
    val weight: Long? = null,
)

/** Known values of [ProfileDetails.gender]; the field is an open string. */
object Gender {
    const val FEMALE = "female"
    const val MALE = "male"
    const val NON_BINARY = "nonBinary"

    val all = listOf(FEMALE, MALE, NON_BINARY)
}

/** Known values of `app.activitytracker.actor.defs#visibility`; the field is an open string. */
object Visibility {
    /** Anyone, so the detail goes in the public profile record. */
    const val PUBLIC = "public"

    /** Accounts the athlete follows that also follow the athlete. */
    const val MUTUALS = "mutuals"

    /** Only the athlete. */
    const val SELF = "self"
}

/** `app.activitytracker.actor.defs#detailsVisibility`: a [Visibility] value per detail group. */
@Immutable
@Serializable
data class DetailsVisibility(
    val location: String? = null,
    val birthDate: String? = null,
    val gender: String? = null,
    val weight: String? = null,
)
