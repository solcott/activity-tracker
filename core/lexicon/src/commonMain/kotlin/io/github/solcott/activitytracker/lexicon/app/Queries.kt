package io.github.solcott.activitytracker.lexicon.app

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.NoOutput
import io.github.solcott.activitytracker.lexicon.NoParams
import io.github.solcott.activitytracker.lexicon.NoParamsSerializer
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.XrpcProcedure
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import kotlinx.serialization.Serializable

object GetTimeline {
    @Serializable data class Params(val limit: Int? = null, val cursor: String? = null)

    @Serializable data class Output(val activities: List<ActivityView>, val cursor: String? = null)

    val method = XrpcQuery(Nsid.FEED_GET_TIMELINE, Params.serializer(), Output.serializer())
}

object GetAuthorActivities {
    @Serializable data class Params(val actor: String, val limit: Int? = null, val cursor: String? = null)

    @Serializable data class Output(val activities: List<ActivityView>, val cursor: String? = null)

    val method = XrpcQuery(Nsid.FEED_GET_AUTHOR_ACTIVITIES, Params.serializer(), Output.serializer())
}

object GetActivity {
    @Serializable data class Params(val uri: AtUri)

    @Serializable data class Output(val activity: ActivityView, val comments: List<CommentView>)

    val method = XrpcQuery(Nsid.FEED_GET_ACTIVITY, Params.serializer(), Output.serializer())
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

/** The caller's own non-public profile details. Requires auth. */
object GetPrivateDetails {
    @Serializable data class Output(val details: ProfileDetails, val visibility: DetailsVisibility)

    val method: XrpcQuery<NoParams, Output> = XrpcQuery(Nsid.ACTOR_GET_PRIVATE_DETAILS, NoParamsSerializer, Output.serializer())
}

/** Replaces the caller's non-public profile details. Every detail sent needs a mutuals or self visibility. Requires auth. */
object PutPrivateDetails {
    @Serializable data class Input(val details: ProfileDetails, val visibility: DetailsVisibility)

    const val INVALID_VISIBILITY = "InvalidVisibility"

    val method = XrpcProcedure(Nsid.ACTOR_PUT_PRIVATE_DETAILS, Input.serializer(), NoOutput.serializer())
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
