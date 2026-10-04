package io.github.solcott.activitytracker.lexicon.app

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
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

/** Page size bounds shared by every paginated query. */
object Paging {
    const val DEFAULT_LIMIT = 30
    const val MAX_LIMIT = 100

    fun clamp(limit: Int?): Int = (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
}
