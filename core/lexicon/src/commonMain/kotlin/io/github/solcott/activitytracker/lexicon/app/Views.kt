package io.github.solcott.activitytracker.lexicon.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** `app.activitytracker.actor.defs#profileViewBasic` */
@Immutable
@Serializable
data class ProfileViewBasic(
    val did: Did,
    val handle: Handle,
    val displayName: String? = null,
    /** URL of the athlete's profile picture. */
    val avatar: String? = null,
)

/** `app.activitytracker.actor.defs#profileView` */
@Immutable
@Serializable
data class ProfileView(
    val did: Did,
    val handle: Handle,
    val displayName: String? = null,
    val description: String? = null,
    /** URL of the athlete's profile picture. */
    val avatar: String? = null,
    /** Only the details this viewer may see. */
    val details: ProfileDetails? = null,
    val units: String? = null,
    /** A [FollowPolicy] value. */
    val followPolicy: String? = null,
    val activitiesCount: Long? = null,
    val followersCount: Long? = null,
    val followsCount: Long? = null,
    val viewer: ProfileViewerState? = null,
)

@Immutable
@Serializable
data class ProfileViewerState(
    /** The viewer's follow record, whether it counts yet or not. */
    val following: AtUri? = null,
    /** True while [following] waits for the athlete's approval. */
    val followRequested: Boolean? = null,
)

/** `app.activitytracker.feed.defs#activityView` */
@Immutable
@Serializable
data class ActivityView(
    val uri: AtUri,
    val cid: Cid,
    val author: ProfileViewBasic,
    val record: ActivityRecord,
    val kudosCount: Long,
    val commentCount: Long,
    val indexedAt: Instant,
    val viewer: ActivityViewerState? = null,
    /** Other recordings of this activity ([ActivityRecord.recordingOf]), oldest first; null when it has none. */
    val recordings: List<RecordingView>? = null,
    /** [record]'s media, with URLs to load them from; null when it has none. */
    val media: List<ActivityMediaView>? = null,
)

@Immutable @Serializable data class ActivityViewerState(val kudos: AtUri? = null)

/** `app.activitytracker.feed.defs#recordingView`: another device's recording of an activity. */
@Immutable @Serializable data class RecordingView(val uri: AtUri, val startedAt: Instant, val device: String? = null)

/** `app.activitytracker.feed.defs#commentView` */
@Immutable
@Serializable
data class CommentView(
    val uri: AtUri,
    val cid: Cid,
    val author: ProfileViewBasic,
    val text: String,
    val createdAt: Instant,
)
