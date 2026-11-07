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
 * [Visibility.FOLLOWERS] or [Visibility.MUTUALS]. The AppView accepts one when [embed] is a [PostEmbed.Proposal] naming the author's own
 * proposal under this record's rkey, or a [PostEmbed.Quote] of another post with non-blank [text] (feed/quote-posts). Clients write one
 * only by sharing a proposal or by quoting a post.
 */
@Serializable
data class PostRecord(
    val embed: PostEmbed,
    val createdAt: Instant,
    /** The author's own words; at most [MAX_TEXT_GRAPHEMES]. Required, and not blank, on a quote. */
    val text: String? = null,
    @SerialName($$"$type") val type: String = Nsid.FEED_POST,
) {
    companion object {
        const val MAX_TEXT_GRAPHEMES = 1000

        /** The lexicon's `maxLength` (UTF-8 bytes) for [text]. */
        const val MAX_TEXT_BYTES = 10_000
    }
}

/**
 * A member of `app.activitytracker.feed.post#embed`, an open union: [Proposal] or [Quote], [Unknown] for anything a newer client writes.
 */
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

    /** `app.activitytracker.feed.post#quote`: quotes the post at [subject] (feed/quote-posts). */
    @Immutable
    @Serializable
    data class Quote(val subject: StrongRef, @SerialName($$"$type") val type: String = TYPE) : PostEmbed {
        companion object {
            const val TYPE = "${Nsid.FEED_POST}#quote"
        }
    }

    @Immutable data class Unknown(val json: JsonObject) : PostEmbed
}

internal object PostEmbedSerializer : OpenUnionSerializer<PostEmbed>("PostEmbed") {
    override fun memberFor(type: String?) =
        when (type) {
            PostEmbed.Proposal.TYPE -> PostEmbed.Proposal.serializer()
            PostEmbed.Quote.TYPE -> PostEmbed.Quote.serializer()
            else -> null
        }

    override fun unknown(json: JsonObject) = PostEmbed.Unknown(json)

    override fun encode(encoder: JsonEncoder, value: PostEmbed) =
        when (value) {
            is PostEmbed.Proposal -> encoder.encodeSerializableValue(PostEmbed.Proposal.serializer(), value)
            is PostEmbed.Quote -> encoder.encodeSerializableValue(PostEmbed.Quote.serializer(), value)
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
    /** Likes the viewer is shown (feed/posts); 0 from an AppView from before likes on posts. */
    val likeCount: Long = 0,
    /** Comments and replies the viewer is shown that aren't hidden, matching `getComments`; 0 from an older AppView. */
    val commentCount: Long = 0,
    /** Reposters the viewer is shown, matching `getReposts` (feed/reposts); 0 from an older AppView. */
    val repostCount: Long = 0,
    /** Quotes of the post the viewer is shown, matching `getQuotes` (feed/quote-posts); 0 from an older AppView. */
    val quoteCount: Long = 0,
    /** Null for a signed-out viewer, and from an older AppView. */
    val viewer: PostViewerState? = null,
    @SerialName($$"$type") val type: String = TYPE,
) {
    val visibilityOrPublic: String
        get() = visibility ?: Visibility.PUBLIC

    companion object {
        const val TYPE = "${Nsid.FEED_DEFS}#postView"
    }
}

/** `app.activitytracker.feed.defs#postViewerState`: the signed-in viewer's [like] on a post and their [repost] of it, if any. */
@Immutable @Serializable data class PostViewerState(val like: AtUri? = null, val repost: AtUri? = null)

/**
 * `app.activitytracker.feed.defs#repostedPostView`: a timeline item showing [post] because of a repost (feed/reposts), placed at
 * [ReasonRepost.createdAt].
 */
@Immutable
@Serializable
data class RepostedPostView(val post: PostView, val reason: ReasonRepost, @SerialName($$"$type") val type: String = TYPE) {
    companion object {
        const val TYPE = "${Nsid.FEED_DEFS}#repostedPostView"
    }
}

/**
 * `app.activitytracker.feed.defs#reasonRepost`: the latest repost putting a post in the viewer's timeline, by [by], and how many [others]
 * there reposted it too.
 */
@Immutable
@Serializable
data class ReasonRepost(
    val by: ProfileViewBasic,
    /** The repost's position: its record's `createdAt`, capped at when it was indexed. */
    val createdAt: Instant,
    val others: Long = 0,
)

/** A member of `app.activitytracker.feed.defs#postView.embed`: [Proposal] or [Quote], [Unknown] for a kind this client can't show. */
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
    ) : PostEmbedView, QuotedPostEmbed {
        val isOpen: Boolean
            get() = status == PostProposalStatus.OPEN

        companion object {
            const val TYPE = "${Nsid.FEED_DEFS}#postProposalEmbedView"
        }
    }

    /** `app.activitytracker.feed.defs#postQuoteEmbedView`: a quote's quoted post, one level deep (feed/quote-posts). */
    @Immutable
    @Serializable
    data class Quote(val quoted: QuotedPost, @SerialName($$"$type") val type: String = TYPE) : PostEmbedView {
        companion object {
            const val TYPE = "${Nsid.FEED_DEFS}#postQuoteEmbedView"
        }
    }

    @Immutable data class Unknown(val json: JsonObject) : PostEmbedView
}

/** `app.activitytracker.feed.defs#postQuoteEmbedView.quoted`, an open union: the quoted [Post], or [Unavailable]. */
@Immutable
@Serializable(with = QuotedPostSerializer::class)
sealed interface QuotedPost {
    /** `app.activitytracker.feed.defs#quotedPostView`: the quoted post as the viewer may see it, without counts or viewer state. */
    @Immutable
    @Serializable
    data class Post(
        val uri: AtUri,
        val cid: Cid,
        val author: ProfileViewBasic,
        val createdAt: Instant,
        val text: String? = null,
        /** A [Visibility] value; null means [Visibility.PUBLIC]. */
        val visibility: String? = null,
        /** Its proposal, or a link when it is itself a quote. */
        val embed: QuotedPostEmbed? = null,
        @SerialName($$"$type") val type: String = TYPE,
    ) : QuotedPost {
        val visibilityOrPublic: String
            get() = visibility ?: Visibility.PUBLIC

        companion object {
            const val TYPE = "${Nsid.FEED_DEFS}#quotedPostView"
        }
    }

    /**
     * `app.activitytracker.feed.defs#quotedPostUnavailable`: the quoted post at [uri] is served to no one, or the quoter may no longer see
     * it.
     */
    @Immutable
    @Serializable
    data class Unavailable(val uri: AtUri, @SerialName($$"$type") val type: String = TYPE) : QuotedPost {
        companion object {
            const val TYPE = "${Nsid.FEED_DEFS}#quotedPostUnavailable"
        }
    }

    @Immutable data class Unknown(val json: JsonObject) : QuotedPost
}

/**
 * `app.activitytracker.feed.defs#quotedPostView.embed`, an open union: a [PostEmbedView.Proposal], a [Link] to a quoted quote's subject, or
 * [Unknown].
 */
@Immutable
@Serializable(with = QuotedPostEmbedSerializer::class)
sealed interface QuotedPostEmbed {
    /** `app.activitytracker.feed.defs#quotedPostLink`: the post a quoted quote quotes, as a link only. */
    @Immutable
    @Serializable
    data class Link(val uri: AtUri, @SerialName($$"$type") val type: String = TYPE) : QuotedPostEmbed {
        companion object {
            const val TYPE = "${Nsid.FEED_DEFS}#quotedPostLink"
        }
    }

    @Immutable data class Unknown(val json: JsonObject) : QuotedPostEmbed
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
    override fun memberFor(type: String?) =
        when (type) {
            PostEmbedView.Proposal.TYPE -> PostEmbedView.Proposal.serializer()
            PostEmbedView.Quote.TYPE -> PostEmbedView.Quote.serializer()
            else -> null
        }

    override fun unknown(json: JsonObject) = PostEmbedView.Unknown(json)

    override fun encode(encoder: JsonEncoder, value: PostEmbedView) =
        when (value) {
            is PostEmbedView.Proposal -> encoder.encodeSerializableValue(PostEmbedView.Proposal.serializer(), value)
            is PostEmbedView.Quote -> encoder.encodeSerializableValue(PostEmbedView.Quote.serializer(), value)
            is PostEmbedView.Unknown -> encoder.encodeJsonElement(value.json)
        }
}

internal object QuotedPostSerializer : OpenUnionSerializer<QuotedPost>("QuotedPost") {
    override fun memberFor(type: String?) =
        when (type) {
            QuotedPost.Post.TYPE -> QuotedPost.Post.serializer()
            QuotedPost.Unavailable.TYPE -> QuotedPost.Unavailable.serializer()
            else -> null
        }

    override fun unknown(json: JsonObject) = QuotedPost.Unknown(json)

    override fun encode(encoder: JsonEncoder, value: QuotedPost) =
        when (value) {
            is QuotedPost.Post -> encoder.encodeSerializableValue(QuotedPost.Post.serializer(), value)
            is QuotedPost.Unavailable -> encoder.encodeSerializableValue(QuotedPost.Unavailable.serializer(), value)
            is QuotedPost.Unknown -> encoder.encodeJsonElement(value.json)
        }
}

internal object QuotedPostEmbedSerializer : OpenUnionSerializer<QuotedPostEmbed>("QuotedPostEmbed") {
    override fun memberFor(type: String?) =
        when (type) {
            PostEmbedView.Proposal.TYPE -> PostEmbedView.Proposal.serializer()
            QuotedPostEmbed.Link.TYPE -> QuotedPostEmbed.Link.serializer()
            else -> null
        }

    override fun unknown(json: JsonObject) = QuotedPostEmbed.Unknown(json)

    override fun encode(encoder: JsonEncoder, value: QuotedPostEmbed) =
        when (value) {
            is PostEmbedView.Proposal -> encoder.encodeSerializableValue(PostEmbedView.Proposal.serializer(), value)
            is QuotedPostEmbed.Link -> encoder.encodeSerializableValue(QuotedPostEmbed.Link.serializer(), value)
            is QuotedPostEmbed.Unknown -> encoder.encodeJsonElement(value.json)
        }
}
