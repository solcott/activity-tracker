package io.github.solcott.activitytracker.lexicon.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.time.Instant
import kotlinx.serialization.SerialName
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
    /** Labels on the athlete's account; null when there are none. */
    val labels: List<LabelView>? = null,
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
    /** Labels on the athlete's account and their profile record; null when there are none. */
    val labels: List<LabelView>? = null,
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
    /** The viewer's own block of the athlete. Never set for the athlete's block of the viewer. */
    val blocking: AtUri? = null,
    /** The viewer's own mute of the athlete. */
    val muting: AtUri? = null,
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
    /**
     * A combined activity's members ([ActivityRecord.combined]) the AppView admits, oldest first; null on an activity that combines
     * nothing. [likeCount] and [commentCount] include the likes and comments on them.
     */
    val members: List<MemberView>? = null,
    /** On a member, the combined activity it's part of; null otherwise. */
    val combinedInto: AtUri? = null,
    /** [record]'s media, with URLs to load them from; null when it has none. */
    val media: List<ActivityMediaView>? = null,
    /**
     * The activity's [ActivityDetailsRecord]s from the tiers the viewer is admitted to, widest first; null when there are none. Read the
     * activity through [effectiveRecord], which lays them over [record].
     */
    val details: List<DetailsView>? = null,
    /** Labels on the activity, which cover its photos too; its author's are on [author]. Null when there are none. */
    val labels: List<LabelView>? = null,
) {
    /** [visibility] as a known value: [Visibility.PUBLIC] when absent, [Visibility.SELF] when unknown. */
    val visibilityOrPublic: String
        get() = Visibility.known(visibility)
}

@Immutable @Serializable data class ActivityViewerState(val like: AtUri? = null)

/**
 * `app.activitytracker.moderation.defs#label`: a label from a labeler the AppView trusts, already checked, newest, neither negated nor
 * expired. [value] is an open set: clients ignore values they don't know.
 */
@Immutable @Serializable data class LabelView(val src: Did, @SerialName("val") val value: String)

/** `app.activitytracker.feed.defs#detailsView`: a details record and the tier ([Visibility] value) it's stored in. */
@Immutable @Serializable data class DetailsView(val visibility: String, val record: ActivityDetailsRecord)

/** `app.activitytracker.feed.defs#memberView`: one recording a combined activity is built from. */
@Immutable
@Serializable
data class MemberView(
    val uri: AtUri,
    val startedAt: Instant,
    /** Seconds. */
    val elapsedTime: Long,
    /** A [Sport] value. */
    val sport: String? = null,
    val device: String? = null,
)

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
    /** Labels on the comment; its commenter's are on [author]. Null when there are none. */
    val labels: List<LabelView>? = null,
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

/** `app.activitytracker.feed.defs#segmentView` */
@Immutable
@Serializable
data class SegmentView(
    val uri: AtUri,
    val cid: Cid,
    val creator: ProfileViewBasic,
    val record: SegmentRecord,
    /** The line's length in meters, computed by the AppView. */
    val distanceMeters: Long,
    /** A [SegmentStatus] value, derived by the AppView; null or unknown means [SegmentStatus.ACTIVE]. */
    val status: String? = null,
    /** The earlier segment this one duplicates, when [status] is [SegmentStatus.DUPLICATE]. */
    val duplicateOf: AtUri? = null,
    val viewer: SegmentViewerState? = null,
)

/** `app.activitytracker.feed.defs#segmentViewerState` */
@Immutable
@Serializable
data class SegmentViewerState(
    /** The viewer's own fastest effort on the segment; null when they have none. */
    val best: EffortView? = null
)

/** `app.activitytracker.feed.defs#segmentViewBasic` */
@Immutable
@Serializable
data class SegmentViewBasic(
    val uri: AtUri,
    val name: String,
    /** A [SegmentSportGroup] value. */
    val sport: String,
    /** The line's length in meters, computed by the AppView. */
    val distanceMeters: Long,
    /** A [SegmentStatus] value; null or unknown means [SegmentStatus.ACTIVE]. */
    val status: String? = null,
)

/**
 * `app.activitytracker.feed.defs#effortView`: one pass of an activity over a segment from start to end. Served only to viewers admitted to
 * the narrowest of the activity's visibility and its Map and Pace/Speed categories.
 */
@Immutable
@Serializable
data class EffortView(
    val segment: SegmentViewBasic,
    val activity: AtUri,
    /** Seconds from the activity's start to the effort's. */
    val startOffset: Long,
    /** Seconds from the segment's start to its end. */
    val elapsed: Long,
    /** The activity's local date, as `YYYY-MM-DD`. */
    val startedOn: String,
    /** The activity is an e-bike ride; null means false. */
    val eBike: Boolean? = null,
    /** The athlete's fastest effort on the segment among those the viewer may see; null means false. */
    val best: Boolean? = null,
    /** 1–10: this best effort's rank on the segment's all-time, everyone board as the viewer sees it; null when not in the top 10. */
    val rank: Int? = null,
    /** A [NotRanked] value saying why leaderboards don't rank this effort; served only to its own athlete, on an active segment. */
    val notRanked: String? = null,
)

/** Known values of [EffortView.notRanked], in order of precedence; an open string. */
object NotRanked {
    /** The athlete hides from leaderboards. */
    const val HIDDEN = "hidden"
    /** The activity is excluded from leaderboards. */
    const val EXCLUDED = "excluded"
    /** Faster than its sport group's cap ([SegmentSportGroup.maxSpeedMmPerS]). */
    const val IMPLAUSIBLE = "implausible"
}

/** `app.activitytracker.feed.defs#leaderboardEntry`: one athlete's place on a segment leaderboard, with their fastest ranked effort. */
@Immutable
@Serializable
data class LeaderboardEntry(
    /** 1 for the fastest; equal times share a rank, and the next rank skips. */
    val rank: Int,
    val athlete: ProfileViewBasic,
    val effort: EffortView,
)
