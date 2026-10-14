package io.github.solcott.activitytracker.lexicon.app

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import kotlin.time.Instant
import kotlinx.serialization.Serializable

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

object GetActivity {
    @Serializable data class Params(val uri: AtUri)

    @Serializable data class Output(val activity: ActivityView, val comments: List<CommentView>)

    val method = XrpcQuery(Nsid.FEED_GET_ACTIVITY, Params.serializer(), Output.serializer())
}

/** An activity's streams, if the viewer may see them; zones only if the viewer may also see the athlete's training. */
object GetActivityStreams {
    @Serializable data class Params(val uri: AtUri)

    @Serializable
    data class Output(
        val uri: AtUri,
        /** A [Visibility] value. */
        val visibility: String,
        val channels: List<String>,
        val data: ActivityStreamData,
        val heartRateZones: ZoneSnapshot? = null,
        val powerZones: ZoneSnapshot? = null,
    )

    val method = XrpcQuery(Nsid.FEED_GET_ACTIVITY_STREAMS, Params.serializer(), Output.serializer())
}

object GetKudos {
    @Serializable data class Params(val uri: AtUri, val limit: Int? = null, val cursor: String? = null)

    @Serializable data class Output(val uri: AtUri, val kudos: List<ProfileViewBasic>, val cursor: String? = null)

    val method = XrpcQuery(Nsid.FEED_GET_KUDOS, Params.serializer(), Output.serializer())
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

/** Athletes of this app matching [Params.q] by handle, display name or DID, best matches first. Requires auth. */
object SearchActors {
    @Serializable data class Params(val q: String, val limit: Int? = null, val cursor: String? = null)

    @Serializable data class Output(val actors: List<ProfileViewBasic>, val cursor: String? = null)

    /** The lexicon's `maxLength` for [Params.q]. */
    const val MAX_QUERY_LENGTH = 100

    val method = XrpcQuery(Nsid.ACTOR_SEARCH_ACTORS, Params.serializer(), Output.serializer())
}

/** Page size bounds shared by every paginated query. */
object Paging {
    const val DEFAULT_LIMIT = 30
    const val MAX_LIMIT = 100

    fun clamp(limit: Int?): Int = (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
}
