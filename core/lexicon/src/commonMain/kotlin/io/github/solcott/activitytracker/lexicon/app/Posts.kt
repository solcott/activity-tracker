package io.github.solcott.activitytracker.lexicon.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject

/**
 * `app.activitytracker.feed.post` (feed/posts): stored in the author's repo when [Visibility.PUBLIC], else in their tier space for
 * [Visibility.FOLLOWERS] or [Visibility.MUTUALS]. For now the AppView accepts one only when [embed] is a [PostEmbed.Proposal] naming the
 * author's own proposal under this record's rkey, and clients write one only by sharing that proposal.
 */
@Serializable
data class PostRecord(
    val embed: PostEmbed,
    val createdAt: Instant,
    /** The author's own words; at most [MAX_TEXT_GRAPHEMES]. */
    val text: String? = null,
    @SerialName($$"$type") val type: String = Nsid.FEED_POST,
) {
    companion object {
        const val MAX_TEXT_GRAPHEMES = 1000

        /** The lexicon's `maxLength` (UTF-8 bytes) for [text]. */
        const val MAX_TEXT_BYTES = 10_000
    }
}

/** A member of `app.activitytracker.feed.post#embed`, an open union: [Proposal] today, [Unknown] for anything a newer client writes. */
@Immutable
@Serializable(with = PostEmbedSerializer::class)
sealed interface PostEmbed {
    /**
     * `app.activitytracker.feed.post#proposal`: shares the author's [SegmentNameProposalRecord] at [proposal], under the post's rkey. A
     * URI, since the post is queued with the proposal before its CID is known; the post shows the proposal's latest version.
     */
    @Immutable
    @Serializable
    data class Proposal(val proposal: AtUri, @SerialName($$"$type") val type: String = TYPE) : PostEmbed {
        companion object {
            const val TYPE = "${Nsid.FEED_POST}#proposal"
        }
    }

    @Immutable data class Unknown(val json: JsonObject) : PostEmbed
}

internal object PostEmbedSerializer : OpenUnionSerializer<PostEmbed>("PostEmbed") {
    override fun memberFor(type: String?) = PostEmbed.Proposal.serializer().takeIf { type == PostEmbed.Proposal.TYPE }

    override fun unknown(json: JsonObject) = PostEmbed.Unknown(json)

    override fun encode(encoder: JsonEncoder, value: PostEmbed) =
        when (value) {
            is PostEmbed.Proposal -> encoder.encodeSerializableValue(PostEmbed.Proposal.serializer(), value)
            is PostEmbed.Unknown -> encoder.encodeJsonElement(value.json)
        }
}

/** `app.activitytracker.feed.defs#postView`: a post the viewer may see, at its tier, with its embed as it stands now. */
@Immutable
@Serializable
data class PostView(
    val uri: AtUri,
    val cid: Cid,
    val author: ProfileViewBasic,
    val embed: PostEmbedView,
    /** As the record gives it, capped at [indexedAt]; places the post on the timeline. */
    val createdAt: Instant,
    val indexedAt: Instant,
    val text: String? = null,
    /** A [Visibility] value, from where the record is stored; null means [Visibility.PUBLIC]. */
    val visibility: String? = null,
    /** Labels on the post; its author's are on [author]. Null when there are none. */
    val labels: List<LabelView>? = null,
    @SerialName($$"$type") val type: String = TYPE,
) {
    val visibilityOrPublic: String
        get() = visibility ?: Visibility.PUBLIC

    companion object {
        const val TYPE = "${Nsid.FEED_DEFS}#postView"
    }
}

/** A member of `app.activitytracker.feed.defs#postView.embed`: [Proposal] today, [Unknown] for a kind this client can't show. */
@Immutable
@Serializable(with = PostEmbedViewSerializer::class)
sealed interface PostEmbedView {
    /**
     * `app.activitytracker.feed.defs#postProposalEmbedView`: a shared name proposal as it stands now. Exactly one of [segment] and
     * [diveSite] is set.
     */
    @Immutable
    @Serializable
    data class Proposal(
        /** The proposal's latest version, which a vote names. */
        val proposal: StrongRef,
        val name: String,
        /** A [PostProposalStatus] value; any other means closed. */
        val status: String,
        val openedAt: Instant,
        /** Counted votes; final once closed. */
        val up: Int,
        val down: Int,
        val segment: SegmentViewBasic? = null,
        val diveSite: DiveSiteViewBasic? = null,
        val viewerVote: SegmentNameViewerVote? = null,
        /** The proposal is open and the viewer has an effort on the segment (a visit to the site). */
        val canVote: Boolean = false,
        @SerialName($$"$type") val type: String = TYPE,
    ) : PostEmbedView {
        val isOpen: Boolean
            get() = status == PostProposalStatus.OPEN

        companion object {
            const val TYPE = "${Nsid.FEED_DEFS}#postProposalEmbedView"
        }
    }

    @Immutable data class Unknown(val json: JsonObject) : PostEmbedView
}

/** `app.activitytracker.feed.defs#postProposalEmbedView.status` values. */
object PostProposalStatus {
    const val OPEN = "open"
    const val ADOPTED = "adopted"
    const val REJECTED = "rejected"
    const val EXPIRED = "expired"
    const val SUPERSEDED = "superseded"
}

internal object PostEmbedViewSerializer : OpenUnionSerializer<PostEmbedView>("PostEmbedView") {
    override fun memberFor(type: String?) = PostEmbedView.Proposal.serializer().takeIf { type == PostEmbedView.Proposal.TYPE }

    override fun unknown(json: JsonObject) = PostEmbedView.Unknown(json)

    override fun encode(encoder: JsonEncoder, value: PostEmbedView) =
        when (value) {
            is PostEmbedView.Proposal -> encoder.encodeSerializableValue(PostEmbedView.Proposal.serializer(), value)
            is PostEmbedView.Unknown -> encoder.encodeJsonElement(value.json)
        }
}
