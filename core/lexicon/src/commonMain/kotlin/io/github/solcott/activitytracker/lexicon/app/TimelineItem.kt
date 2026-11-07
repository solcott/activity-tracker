package io.github.solcott.activitytracker.lexicon.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.Nsid
import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * `app.activitytracker.feed.defs#importCardView`: one timeline item standing for [author]'s batch import ([ActivityRecord.importBatch]),
 * counting the batch's backfilled activities the viewer may see. Everything on it comes from those activities as the viewer may see them.
 */
@Immutable
@Serializable
data class ImportCardView(
    val author: ProfileViewBasic,
    /** The batch's [ActivityRecord.importBatch]. */
    val batch: String,
    val count: Long,
    val earliestStartedAt: Instant,
    val latestStartedAt: Instant,
    /** How many of each sport, most first. */
    val sports: List<SportCount>,
    /** Meters: the total of the distances the activities' own records give; null when none gives one. */
    val distance: Long? = null,
    /** Routes of up to [MAX_ROUTES] of the activities, latest started first. */
    val routes: List<CardRoute>? = null,
    /** The latest post time among the activities, which places the card. */
    val postedAt: Instant,
    @SerialName($$"$type") val type: String = TYPE,
) {
    companion object {
        const val TYPE = "${Nsid.FEED_DEFS}#importCardView"

        /** The lexicon's `maxLength` for [routes]. */
        const val MAX_ROUTES = 4
    }
}

/** `app.activitytracker.feed.defs#sportCount` */
@Immutable @Serializable data class SportCount(val sport: String, val count: Long)

/** `app.activitytracker.feed.defs#cardRoute`: a route as [ActivityRecord.polyline] and [ActivityRecord.polylineGaps] give it. */
@Immutable @Serializable data class CardRoute(val polyline: String, val polylineGaps: List<Int>? = null)

/**
 * A member of `app.activitytracker.feed.getTimeline#feed`, an open union: an [Activity], an [ImportCard], a [Post] or a [Repost]. Any other
 * kind a newer AppView serves decodes as [Unknown], which clients skip.
 */
@Immutable
@Serializable(with = TimelineItemSerializer::class)
sealed interface TimelineItem {
    /** `app.activitytracker.feed.defs#activityView`. [ActivityView] has no `$type` of its own, so this one writes it. */
    @Immutable data class Activity(val view: ActivityView) : TimelineItem

    @Immutable data class ImportCard(val card: ImportCardView) : TimelineItem

    /** `app.activitytracker.feed.defs#postView` (feed/posts). */
    @Immutable data class Post(val post: PostView) : TimelineItem

    /** `app.activitytracker.feed.defs#repostedPostView` (feed/reposts). */
    @Immutable data class Repost(val reposted: RepostedPostView) : TimelineItem

    @Immutable data class Unknown(val json: JsonObject) : TimelineItem

    companion object {
        const val ACTIVITY_TYPE = "${Nsid.FEED_DEFS}#activityView"
    }
}

internal object TimelineItemSerializer : OpenUnionSerializer<TimelineItem>("TimelineItem") {
    private object ActivityMember : KSerializer<TimelineItem.Activity> {
        override val descriptor = ActivityView.serializer().descriptor

        override fun deserialize(decoder: Decoder) = TimelineItem.Activity(decoder.decodeSerializableValue(ActivityView.serializer()))

        override fun serialize(encoder: Encoder, value: TimelineItem.Activity) =
            encoder.encodeSerializableValue(ActivityView.serializer(), value.view)
    }

    private object CardMember : KSerializer<TimelineItem.ImportCard> {
        override val descriptor = ImportCardView.serializer().descriptor

        override fun deserialize(decoder: Decoder) = TimelineItem.ImportCard(decoder.decodeSerializableValue(ImportCardView.serializer()))

        override fun serialize(encoder: Encoder, value: TimelineItem.ImportCard) =
            encoder.encodeSerializableValue(ImportCardView.serializer(), value.card)
    }

    private object PostMember : KSerializer<TimelineItem.Post> {
        override val descriptor = PostView.serializer().descriptor

        override fun deserialize(decoder: Decoder) = TimelineItem.Post(decoder.decodeSerializableValue(PostView.serializer()))

        override fun serialize(encoder: Encoder, value: TimelineItem.Post) =
            encoder.encodeSerializableValue(PostView.serializer(), value.post)
    }

    private object RepostMember : KSerializer<TimelineItem.Repost> {
        override val descriptor = RepostedPostView.serializer().descriptor

        override fun deserialize(decoder: Decoder) = TimelineItem.Repost(decoder.decodeSerializableValue(RepostedPostView.serializer()))

        override fun serialize(encoder: Encoder, value: TimelineItem.Repost) =
            encoder.encodeSerializableValue(RepostedPostView.serializer(), value.reposted)
    }

    override fun memberFor(type: String?) =
        when (type) {
            TimelineItem.ACTIVITY_TYPE -> ActivityMember
            ImportCardView.TYPE -> CardMember
            PostView.TYPE -> PostMember
            RepostedPostView.TYPE -> RepostMember
            else -> null
        }

    override fun unknown(json: JsonObject) = TimelineItem.Unknown(json)

    override fun encode(encoder: JsonEncoder, value: TimelineItem) =
        when (value) {
            is TimelineItem.Activity -> {
                val view = encoder.json.encodeToJsonElement(ActivityView.serializer(), value.view).jsonObject
                encoder.encodeJsonElement(
                    buildJsonObject {
                        put($$"$type", JsonPrimitive(TimelineItem.ACTIVITY_TYPE))
                        view.forEach { (k, v) -> put(k, v) }
                    }
                )
            }
            is TimelineItem.ImportCard -> encoder.encodeSerializableValue(ImportCardView.serializer(), value.card)
            is TimelineItem.Post -> encoder.encodeSerializableValue(PostView.serializer(), value.post)
            is TimelineItem.Repost -> encoder.encodeSerializableValue(RepostedPostView.serializer(), value.reposted)
            is TimelineItem.Unknown -> encoder.encodeJsonElement(value.json)
        }
}
