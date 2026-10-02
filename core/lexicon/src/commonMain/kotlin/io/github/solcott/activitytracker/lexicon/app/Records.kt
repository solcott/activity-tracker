package io.github.solcott.activitytracker.lexicon.app

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

@Serializable
data class ProfileRecord(
    val displayName: String? = null,
    val description: String? = null,
    val units: String? = null,
    val sports: List<String> = emptyList(),
    val createdAt: Instant? = null,
    @SerialName("\$type") val type: String = Nsid.ACTOR_PROFILE,
) {
    companion object {
        const val RKEY = "self"
    }
}
