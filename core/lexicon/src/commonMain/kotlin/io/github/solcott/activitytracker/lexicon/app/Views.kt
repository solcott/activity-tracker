package io.github.solcott.activitytracker.lexicon.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
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
    /** The athlete's follow of the viewer, only once it counts (not while it waits for the viewer's approval). */
    val followedBy: AtUri? = null,
)

/** `app.activitytracker.feed.defs#activityView` */
@Immutable
@Serializable
data class ActivityView(
    val uri: AtUri,
    val cid: Cid,
    val author: ProfileViewBasic,
    val record: ActivityRecord,
    /** Likes from athletes who, like the viewer, may see the activity at its current [visibility]. */
    val likeCount: Long,
    /** Comments from athletes who may see the activity at its current [visibility], written for an audience that admits the viewer. */
    val commentCount: Long,
    val indexedAt: Instant,
    /** A [Visibility] value, from where the record is stored; null means [Visibility.PUBLIC]. Read it through [visibilityOrPublic]. */
    val visibility: String? = null,
    val viewer: ActivityViewerState? = null,
    /** Other recordings of this activity ([ActivityRecord.recordingOf]), oldest first; null when it has none. */
    val recordings: List<RecordingView>? = null,
    /** [record]'s media, with URLs to load them from; null when it has none. */
    val media: List<ActivityMediaView>? = null,
) {
    /** [visibility] as a known value: [Visibility.PUBLIC] when absent, [Visibility.SELF] when unknown. */
    val visibilityOrPublic: String
        get() = Visibility.known(visibility)
}

@Immutable @Serializable data class ActivityViewerState(val like: AtUri? = null)

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
    /** The activity's athlete hid it. Set only for the athlete and the commenter, the only viewers a hidden comment is listed for. */
    val hidden: Boolean? = null,
    /** The [Visibility] it was written for, which caps a reply to it. Null means [Visibility.PUBLIC]. */
    val audience: String? = null,
    /** On a reply, its thread's top-level comment ([ReplyRef.root]). */
    val root: StrongRef? = null,
    /** On a reply, the comment it answers ([ReplyRef.parent]). */
    val parent: StrongRef? = null,
) {
    /** This comment as a strong ref, for a reply to it. */
    val ref: StrongRef
        get() = StrongRef(uri, cid)
}

/**
 * `app.activitytracker.feed.defs#threadView`: a top-level comment and its oldest replies listed for the viewer. A null [root] is the
 * placeholder for a top-level comment not listed for the viewer while some of its replies are; nothing says why.
 */
@Immutable
@Serializable
data class ThreadView(
    /** The top-level comment's URI. */
    val uri: AtUri,
    val root: CommentView? = null,
    /** At most three, oldest first. */
    val replies: List<CommentView>,
    /** Replies listed for the viewer, of which [replies] are the first. */
    val replyCount: Int,
    /** For [GetComments.Params.root], when [replyCount] is more than [replies] holds. */
    val repliesCursor: String? = null,
)
