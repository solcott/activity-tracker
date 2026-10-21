package io.github.solcott.activitytracker.lexicon.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.NoParamsSerializer
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.XrpcProcedure
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** The optional feed filters shared by [GetTimeline] and [GetAuthorActivities]. */
object FeedFilterParams {
    /** Most `type` values one request may carry. */
    const val MAX_TYPES = 20

    /** Longest `type` value: a sport and a sub-sport (each up to 64 chars) joined by [TYPE_SEPARATOR]. */
    const val MAX_TYPE_LENGTH = 129

    const val TYPE_SEPARATOR = ':'
}

object GetTimeline {
    @Serializable
    data class Params(
        val limit: Int? = null,
        val cursor: String? = null,
        /** Only these types, any one matching: a [Sport] (every sub-sport) or `sport:subSport` (exactly). See [FeedFilterParams]. */
        val type: List<String>? = null,
        /** Only activities that started at or after this. */
        val since: Instant? = null,
        /** Only activities that started before this. */
        val until: Instant? = null,
    )

    @Serializable data class Output(val activities: List<ActivityView>, val cursor: String? = null)

    val method = XrpcQuery(Nsid.FEED_GET_TIMELINE, Params.serializer(), Output.serializer())
}

object GetAuthorActivities {
    @Serializable
    data class Params(
        val actor: String,
        val limit: Int? = null,
        val cursor: String? = null,
        /** As [GetTimeline.Params.type]. */
        val type: List<String>? = null,
        /** As [GetTimeline.Params.since]. */
        val since: Instant? = null,
        /** As [GetTimeline.Params.until]. */
        val until: Instant? = null,
    )

    @Serializable data class Output(val activities: List<ActivityView>, val cursor: String? = null)

    val method = XrpcQuery(Nsid.FEED_GET_AUTHOR_ACTIVITIES, Params.serializer(), Output.serializer())
}

/** An athlete's activities that started in [since, until), oldest first; finds what a file import would duplicate. */
object GetAuthorActivitiesByStart {
    @Serializable
    data class Params(
        val actor: String,
        /** Inclusive. */
        val since: Instant,
        /** Exclusive. */
        val until: Instant,
        /** Only this [Sport]. */
        val sport: String? = null,
        val limit: Int? = null,
        val cursor: String? = null,
    )

    @Serializable data class Output(val activities: List<ActivityView>, val cursor: String? = null)

    val method = XrpcQuery(Nsid.FEED_GET_AUTHOR_ACTIVITIES_BY_START, Params.serializer(), Output.serializer())
}

/** A single activity. Its comments come from [GetComments]. */
object GetActivity {
    @Serializable data class Params(val uri: AtUri)

    @Serializable
    data class Output(
        val activity: ActivityView,
        /** Deprecated: always empty, and still written (`[]`) only for clients from before [GetComments]. */
        val comments: List<CommentView> = emptyList(),
    )

    val method = XrpcQuery(Nsid.FEED_GET_ACTIVITY, Params.serializer(), Output.serializer())
}

/**
 * `app.activitytracker.moderation.getSubject`: for moderators, a reported activity or comment at any visibility, with photo links signed
 * for them; one that isn't Public only while it's under review.
 */
object GetSubject {
    @Serializable data class Params(val uri: AtUri)

    @Serializable
    data class Output(
        /** A [Visibility] value. */
        val visibility: String,
        val activity: ActivityView? = null,
        val comment: CommentView? = null,
    )

    val method = XrpcQuery(Nsid.MODERATION_GET_SUBJECT, Params.serializer(), Output.serializer())
}

/** `app.activitytracker.moderation.getRole`: the caller's role on the app's labeler team; null when they're not on it. */
object GetModerationRole {
    /** The roles that may open subjects. */
    val MODERATOR_ROLES = setOf("admin", "moderator")

    @Serializable data class Output(val role: String? = null)

    val method = XrpcQuery(Nsid.MODERATION_GET_ROLE, NoParamsSerializer, Output.serializer())
}

/** An activity's streams, if the viewer may see them; zones only if the viewer may also see its [ActivityZonesRecord] where it's stored. */
object GetActivityStreams {
    @Serializable data class Params(val uri: AtUri)

    @Serializable
    data class Output(
        val uri: AtUri,
        /** A [Visibility] value: the widest of [parts]. */
        val visibility: String,
        val channels: List<String>,
        /** Merged from every part, a narrower part's channels winning ([ActivityStreamData.overlaid]). */
        val data: ActivityStreamData,
        val heartRateZones: ZoneSnapshot? = null,
        val powerZones: ZoneSnapshot? = null,
        /** From the activity's zones record: left out, like the zones, unless the viewer may see that record. */
        val trainingLoad: TrainingLoad? = null,
        /** Each streams record served, widest first. Null from an AppView that predates detail categories: one part at [visibility]. */
        val parts: List<StreamsPart>? = null,
    )

    /** `#part`: one streams record served, by the tier ([Visibility] value) it's stored in, and the channels it holds. */
    @Serializable data class StreamsPart(val visibility: String, val channels: List<String>)

    val method = XrpcQuery(Nsid.FEED_GET_ACTIVITY_STREAMS, Params.serializer(), Output.serializer())
}

object GetLikes {
    @Serializable data class Params(val uri: AtUri, val limit: Int? = null, val cursor: String? = null)

    @Serializable data class Output(val uri: AtUri, val likes: List<ProfileViewBasic>, val cursor: String? = null)

    val method = XrpcQuery(Nsid.FEED_GET_LIKES, Params.serializer(), Output.serializer())
}

/**
 * An activity's comments, oldest first, a page at a time ([limit], [cursor]), by the same rule as its `commentCount`, plus hidden ones
 * (marked) for its athlete and their commenters. Given [Params.uris], just those of them listed for the viewer instead, with no cursor.
 */
object GetComments {
    /** Most [Params.uris] one request may carry. */
    const val MAX_URIS = 25

    @Serializable
    data class Params(
        /** The activity. */
        val uri: AtUri,
        val limit: Int? = null,
        val cursor: String? = null,
        /** Lookup mode: instead of a page, these comments and replies only, at most [MAX_URIS]; [limit] and [cursor] are ignored. */
        val uris: List<AtUri>? = null,
        /** Thread mode: a page of this top-level comment's replies in [Output.comments]. Not allowed with [uris]. */
        val root: AtUri? = null,
    )

    @Serializable
    data class Output(
        val uri: AtUri,
        /** Page mode: the page's listed top-level comments, for clients from before replies. Lookup and thread mode: the result. */
        val comments: List<CommentView>,
        val cursor: String? = null,
        /** Page mode only: the page's threads. */
        val threads: List<ThreadView>? = null,
    )

    val method = XrpcQuery(Nsid.FEED_GET_COMMENTS, Params.serializer(), Output.serializer())
}

object GetProfile {
    @Serializable data class Params(val actor: String)

    val method = XrpcQuery(Nsid.ACTOR_GET_PROFILE, Params.serializer(), ProfileView.serializer())
}

object GetFollowers {
    @Serializable data class Params(val actor: String, val limit: Int? = null, val cursor: String? = null)

    @Serializable data class Output(val subject: ProfileViewBasic, val followers: List<ProfileViewBasic>, val cursor: String? = null)

    val method = XrpcQuery(Nsid.GRAPH_GET_FOLLOWERS, Params.serializer(), Output.serializer())
}

object GetFollows {
    @Serializable data class Params(val actor: String, val limit: Int? = null, val cursor: String? = null)

    @Serializable data class Output(val subject: ProfileViewBasic, val follows: List<ProfileViewBasic>, val cursor: String? = null)

    val method = XrpcQuery(Nsid.GRAPH_GET_FOLLOWS, Params.serializer(), Output.serializer())
}

/** Accounts whose follows of the caller wait for approval, newest first. Includes ones the caller declined. Requires auth. */
object GetFollowRequests {
    @Serializable data class Params(val limit: Int? = null, val cursor: String? = null)

    @Serializable data class Output(val requests: List<ProfileViewBasic>, val cursor: String? = null)

    val method = XrpcQuery(Nsid.GRAPH_GET_FOLLOW_REQUESTS, Params.serializer(), Output.serializer())
}

/** Accounts the caller blocks, from either kind of block record, newest first. Requires auth. */
object GetBlocks {
    @Serializable data class Params(val limit: Int? = null, val cursor: String? = null)

    @Serializable data class Output(val blocks: List<BlockView>, val cursor: String? = null)

    /** `#blockView`: [sources] holds [BlockSource] values; only an [BlockSource.APP] block can be removed by this app. */
    @Immutable @Serializable data class BlockView(val actor: ProfileViewBasic, val sources: List<String>, val createdAt: Instant)

    val method = XrpcQuery(Nsid.GRAPH_GET_BLOCKS, Params.serializer(), Output.serializer())
}

/** Known values of [GetBlocks.BlockView.sources]. */
object BlockSource {
    /** An `app.activitytracker.graph.block` in the athlete's interactions space. */
    const val APP = "app"

    /** An `app.bsky.graph.block` in the athlete's repo. */
    const val BLUESKY = "bluesky"
}

/** Accounts the caller mutes, newest first. Requires auth. */
object GetMutes {
    @Serializable data class Params(val limit: Int? = null, val cursor: String? = null)

    @Serializable data class Output(val mutes: List<ProfileViewBasic>, val cursor: String? = null)

    val method = XrpcQuery(Nsid.GRAPH_GET_MUTES, Params.serializer(), Output.serializer())
}

/** Athletes of this app matching [Params.q] by handle, display name or DID, best matches first. Requires auth. */
object SearchActors {
    @Serializable data class Params(val q: String, val limit: Int? = null, val cursor: String? = null)

    @Serializable data class Output(val actors: List<ProfileViewBasic>, val cursor: String? = null)

    /** The lexicon's `maxLength` for [Params.q]. */
    const val MAX_QUERY_LENGTH = 100

    val method = XrpcQuery(Nsid.ACTOR_SEARCH_ACTORS, Params.serializer(), Output.serializer())
}

/** A segment, with the signed-in viewer's own fastest effort on it. `NotFound` when it isn't indexed. */
object GetSegment {
    @Serializable data class Params(val uri: AtUri)

    @Serializable data class Output(val segment: SegmentView)

    val method = XrpcQuery(Nsid.FEED_GET_SEGMENT, Params.serializer(), Output.serializer())
}

/** Segments whose line passes through a box (1e-7 degrees), at most [MAX_SPAN_E7] on either side, else `InvalidRequest`. */
object SearchSegments {
    @Serializable
    data class Params(
        val minLat: Int,
        val minLng: Int,
        val maxLat: Int,
        val maxLng: Int,
        /** Only this [SegmentSportGroup]. */
        val sport: String? = null,
        val limit: Int? = null,
        val cursor: String? = null,
    )

    @Serializable data class Output(val segments: List<SegmentView>, val cursor: String? = null)

    /** One degree, in 1e-7 degrees. */
    const val MAX_SPAN_E7 = 10_000_000

    val method = XrpcQuery(Nsid.FEED_SEARCH_SEGMENTS, Params.serializer(), Output.serializer())
}

/** The efforts on an activity the viewer may see, in the order they began; unpaged. `NotFound` when the viewer may not see the activity. */
object GetActivitySegments {
    @Serializable data class Params(val activity: AtUri)

    @Serializable data class Output(val efforts: List<EffortView>)

    val method = XrpcQuery(Nsid.FEED_GET_ACTIVITY_SEGMENTS, Params.serializer(), Output.serializer())
}

/** One athlete's efforts on a segment the viewer may see, fastest first. `NotFound` when the segment isn't indexed. */
object GetSegmentEfforts {
    @Serializable data class Params(val segment: AtUri, val actor: String, val limit: Int? = null, val cursor: String? = null)

    @Serializable data class Output(val efforts: List<EffortView>, val cursor: String? = null)

    val method = XrpcQuery(Nsid.FEED_GET_SEGMENT_EFFORTS, Params.serializer(), Output.serializer())
}

/**
 * A segment's leaderboard (segments/segment-leaderboards): each athlete's fastest ranked effort the viewer may see, fastest first, plus the
 * viewer's own entry. Only an active segment has entries. `NotFound` when the segment isn't served; `InvalidRequest` for an unknown
 * [Params.period] or [Params.scope], or [LeaderboardScope.FOLLOWING] without a viewer.
 */
object GetSegmentLeaderboard {
    @Serializable
    data class Params(
        val segment: AtUri,
        /** A [LeaderboardPeriod] value; null means [LeaderboardPeriod.ALL_TIME]. */
        val period: String? = null,
        /** A [LeaderboardScope] value; null means [LeaderboardScope.EVERYONE]. */
        val scope: String? = null,
        /** The e-bike board; null means false. */
        val eBike: Boolean? = null,
        val limit: Int? = null,
        val cursor: String? = null,
    )

    @Serializable data class Output(val entries: List<LeaderboardEntry>, val viewer: LeaderboardEntry? = null, val cursor: String? = null)

    val method = XrpcQuery(Nsid.FEED_GET_SEGMENT_LEADERBOARD, Params.serializer(), Output.serializer())
}

/** Known values of [GetSegmentLeaderboard.Params.period]: by the effort's local date, against the AppView's UTC date. */
object LeaderboardPeriod {
    const val ALL_TIME = "allTime"
    const val YEAR = "year"
    const val MONTH = "month"

    val all = listOf(ALL_TIME, YEAR, MONTH)
}

/** Known values of [GetSegmentLeaderboard.Params.scope]. */
object LeaderboardScope {
    const val EVERYONE = "everyone"
    /** The viewer and the athletes they effectively follow. */
    const val FOLLOWING = "following"

    val all = listOf(EVERYONE, FOLLOWING)
}

/**
 * Checks a line before the caller publishes it as a segment (segments/segment-records): the proposed or active segments of [Input.sport] it
 * would duplicate, earliest indexed first, and how many more new segments the caller may create today. `InvalidRequest` for a malformed
 * line, one outside its group's length bounds, or an unknown group.
 */
object CheckSegment {
    @Serializable
    data class Input(
        /** A [SegmentSportGroup] value. */
        val sport: String,
        /** The line as an encoded polyline at precision 6, as [SegmentRecord.line]. */
        val line: String,
    )

    @Serializable data class Output(val duplicates: List<SegmentView>, val remaining: Int)

    val method = XrpcProcedure(Nsid.FEED_CHECK_SEGMENT, Input.serializer(), Output.serializer())
}

/** Page size bounds shared by every paginated query. */
object Paging {
    const val DEFAULT_LIMIT = 30
    const val MAX_LIMIT = 100

    fun clamp(limit: Int?): Int = (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
}

/**
 * `app.activitytracker.space.notifySpace`: the caller's [AthleteSpace] tier or interactions space at [Input.space] exists, so the AppView
 * syncs it.
 */
object NotifySpace {
    @Serializable data class Input(val space: SpaceUri)

    /** Errors the AppView answers with. */
    const val NOT_SPACE_AUTHORITY = "NotSpaceAuthority"
    const val UNSUPPORTED_SPACE = "UnsupportedSpace"

    val method = XrpcProcedure(Nsid.SPACE_NOTIFY_SPACE, Input.serializer(), JsonObject.serializer())
}

// The segment importer's calls (`app.activitytracker.importer.*`), which only the AppView's configured importer account may make.

/**
 * `app.activitytracker.importer.defs#candidate`: a candidate segment staged in the AppView before it's published, validated as a
 * [SegmentRecord] is. Never served by any feed query.
 */
@Serializable
data class SegmentCandidate(
    /** The importer's stable id: [SegmentCandidates.ID_PATTERN]. */
    val id: String,
    /** A [SegmentSportGroup] value. */
    val sport: String,
    val name: String,
    /** The line as an encoded polyline at precision 6, as [SegmentRecord.line]. */
    val line: String,
    /** A [SegmentSource] value. */
    val source: String,
    /** An SPDX identifier, as [SegmentRecord.license]. */
    val license: String,
)

/** `app.activitytracker.importer.defs#rejection`: a staged candidate the AppView refused, and why. */
@Serializable data class SegmentCandidateRejection(val id: String, val reason: String)

/** Limits shared by the candidate calls. */
object SegmentCandidates {
    /** Most candidates in one [PutSegmentCandidates] batch or one [GetRiddenSegmentCandidates] page. */
    const val MAX_BATCH = 500

    /** Longest revision. */
    const val MAX_REVISION_LENGTH = 64

    /** Longest candidate id or region id. */
    const val MAX_ID_LENGTH = 64

    /** What a candidate id and a region id must match. */
    val ID_PATTERN = Regex("[a-z0-9-]{1,$MAX_ID_LENGTH}")

    /** The error a caller other than the configured importer gets. */
    const val NOT_IMPORTER = "NotImporter"
}

/**
 * `app.activitytracker.importer.putSegmentCandidates`: stages up to [SegmentCandidates.MAX_BATCH] of a region's candidates for one import
 * run, storing or replacing each by its id; returns those that failed validation.
 */
object PutSegmentCandidates {
    @Serializable data class Input(val region: String, val revision: String, val candidates: List<SegmentCandidate>)

    @Serializable data class Output(val rejected: List<SegmentCandidateRejection>)

    val method = XrpcProcedure(Nsid.IMPORTER_PUT_SEGMENT_CANDIDATES, Input.serializer(), Output.serializer())
}

/** `app.activitytracker.importer.finishSegmentCandidates`: removes the region's candidates not staged in [Input.revision]. */
object FinishSegmentCandidates {
    @Serializable data class Input(val region: String, val revision: String)

    @Serializable data class Output(val removed: Int)

    val method = XrpcProcedure(Nsid.IMPORTER_FINISH_SEGMENT_CANDIDATES, Input.serializer(), Output.serializer())
}

/** `app.activitytracker.importer.getRiddenSegmentCandidates`: a region's ridden candidates by id, with no counts, athletes or dates. */
object GetRiddenSegmentCandidates {
    @Serializable data class Params(val region: String, val limit: Int? = null, val cursor: String? = null)

    @Serializable data class Output(val candidates: List<SegmentCandidate>, val cursor: String? = null)

    val method = XrpcQuery(Nsid.IMPORTER_GET_RIDDEN_SEGMENT_CANDIDATES, Params.serializer(), Output.serializer())
}
