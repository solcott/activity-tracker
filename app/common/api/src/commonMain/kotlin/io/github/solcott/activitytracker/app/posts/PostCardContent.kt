package io.github.solcott.activitytracker.app.posts

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.feed.isPending
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostProposalStatus
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.QuotedPost
import io.github.solcott.activitytracker.lexicon.app.QuotedPostEmbed
import io.github.solcott.activitytracker.lexicon.app.ReasonRepost
import io.github.solcott.activitytracker.lexicon.app.RepostedPostView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import kotlinx.collections.immutable.persistentListOf

/**
 * What one post card (feed/posts) shows of [post] for a viewer who wrote it ([isOwn]) or not, in a timeline through [reason]'s repost
 * (feed/reposts) or on its own, chosen once for the shared and the native card (dev/shared-display-text): who reposted it, the author and
 * audience, the embed (a proposal with its votes, or a quote's quoted post, feed/quote-posts), the buttons it offers and the counts. Each
 * UI only lays it out, and builds the [PostEvent]s its taps send.
 */
@Immutable
@Suppress("TooManyFunctions") // One per line of the card, so both UIs ask the same.
class PostCardContent(val post: PostView, val isOwn: Boolean, val viewerDid: String, val reason: ReasonRepost? = null) {
    /** The author, by the name the app shows them. */
    fun author(): AppText = AppText.written(post.author.shownName)

    /** Who may see it, beside the author. */
    fun audience(): AppText = LabelText.visibility(post.visibilityOrPublic)

    /** The author's own words, if any. */
    val text: String?
        get() = post.text

    /** Who put it in the viewer's timeline, whose profile "Reposted by" opens; null off a timeline or for a post there on its own. */
    val reposter: ProfileViewBasic?
        get() = reason?.by

    /** "Reposted by you", "Reposted by Bob", or "Reposted by Bob and 2 others"; null without a repost. */
    fun repostedBy(): AppText? {
        val reason = reason ?: return null
        return when {
            reason.by.did.value == viewerDid -> AppText.named("post_reposted_by_you")
            reason.others > 0 ->
                AppText.Plural(
                    "post_reposted_by_others",
                    reason.others.toInt(),
                    persistentListOf(AppText.written(reason.by.shownName), AppText.written(formatWhole(reason.others))),
                )
            else -> AppText.named("post_reposted_by", AppText.written(reason.by.shownName))
        }
    }

    // The embed. Typed getters, since Swift can't switch over a sealed embed whose Unknown member holds a JsonObject.

    /** The proposal it shares, as it stands now; null for a quote or an unknown embed. */
    val proposal: PostEmbedView.Proposal?
        get() = post.embed as? PostEmbedView.Proposal

    /** "Name for segment Dog Hill" or "Name for dive site Blue Hole", which opens that page; null without a proposal. */
    fun place(): AppText? {
        val proposal = proposal ?: return null
        return proposal.segment?.let { AppText.named("post_card_segment", AppText.written(it.name)) }
            ?: proposal.diveSite?.let { AppText.named("post_card_dive_site", AppText.written(it.name)) }
            ?: AppText.written("")
    }

    /** "Proposes “Wiggly Woods”"; null without a proposal. */
    fun proposes(): AppText? = proposal?.let { proposesName(it.name) }

    /** "Open for votes · 4 up · 1 down"; null without a proposal. */
    fun proposalStatus(): AppText? = proposal?.let {
        AppText.named(
            "text_dot_pair",
            status(it.status),
            AppText.named("segment_name_totals", AppText.written(formatWhole(it.up)), AppText.written(formatWhole(it.down))),
        )
    }

    /** The up and down votes are offered: the proposal is open and the viewer has an effort on its segment or a visit to its site. */
    val canVote: Boolean
        get() = proposal?.canVote == true

    val votedUp: Boolean
        get() = proposal?.viewerVote?.direction == SegmentNameVoteDirection.UP

    val votedDown: Boolean
        get() = proposal?.viewerVote?.direction == SegmentNameVoteDirection.DOWN

    /** What a screen reader says for the up vote. */
    fun voteUp(): AppText = AppText.named("segment_name_vote_up", AppText.written(proposal?.name.orEmpty()))

    /** What a screen reader says for the down vote. */
    fun voteDown(): AppText = AppText.named("segment_name_vote_down", AppText.written(proposal?.name.orEmpty()))

    /** What a tap on the up vote ([up]) or the down vote sends: that vote, or none when it is already the viewer's. */
    fun vote(up: Boolean): PostEvent {
        val direction = if (up) SegmentNameVoteDirection.UP else SegmentNameVoteDirection.DOWN
        return PostEvent.Vote(post, direction.takeIf { it != proposal?.viewerVote?.direction })
    }

    /** The quoted post, one level deep, when the viewer may see it; null for a proposal, or when it's unavailable. */
    val quoted: QuotedPost.Post?
        get() = (post.embed as? PostEmbedView.Quote)?.quoted as? QuotedPost.Post

    /** A quote whose quoted post the viewer can't see, or of a kind this version doesn't know: "Post unavailable", which opens nothing. */
    val quotedUnavailable: Boolean
        get() = (post.embed as? PostEmbedView.Quote)?.quoted.let { it != null && it !is QuotedPost.Post }

    /** [quotedUnavailable]'s words. */
    fun quotedUnavailableText(): AppText = AppText.named("quoted_post_unavailable")

    /** "Bob · Followers" over the quoted post; null without one. */
    fun quotedHeader(): AppText? = quoted?.let {
        AppText.named("text_dot_pair", AppText.written(it.author.shownName), LabelText.visibility(it.visibilityOrPublic))
    }

    /** "Proposes “Wiggly Woods”" for a quoted post sharing a proposal; null otherwise. */
    fun quotedProposes(): AppText? = (quoted?.embed as? PostEmbedView.Proposal)?.let { proposesName(it.name) }

    /** The post a quoted quote itself quotes, which "Quoting another post" opens; null otherwise. */
    val quotedLink: AtUri?
        get() = (quoted?.embed as? QuotedPostEmbed.Link)?.uri

    /** [quotedLink]'s words. */
    fun quotedLinkText(): AppText = AppText.named("quoted_post_link")

    /** What a screen reader says a tap on the quoted post or its link does. */
    fun quotedOpen(): AppText = AppText.named("quoted_post_open")

    /** An embed this version can't show, which says so in its place. */
    val embedUnknown: Boolean
        get() = post.embed is PostEmbedView.Unknown

    /** [embedUnknown]'s words. */
    fun embedUnknownText(): AppText = AppText.named("post_card_unavailable")

    // The buttons and counts.

    /** Like is offered on others' posts only. */
    val offersLike: Boolean
        get() = !isOwn

    val liked: Boolean
        get() = post.viewer?.like != null

    /** Repost is offered on others' posts only (feed/reposts). */
    val offersRepost: Boolean
        get() = !isOwn

    val reposted: Boolean
        get() = post.viewer?.repost != null

    /** Repost's label: Undo repost once the viewer has reposted it. */
    fun repostLabel(): AppText = AppText.named(if (reposted) "undo_repost_content_description" else "repost_content_description")

    /** Quote is offered on any post once it's listed: a quote names its post by CID, which a post sent this session doesn't have yet. */
    val offersQuote: Boolean
        get() = !post.isPending

    /** "2 likes · 1 comment · 0 reposts · 1 quote". */
    fun counts(): AppText =
        AppText.named(
            "post_card_counts",
            plural("like_count", post.likeCount),
            plural("comments_count", post.commentCount),
            plural("repost_count", post.repostCount),
            plural("quote_count", post.quoteCount),
        )

    /** Delete is offered on the viewer's own quote, after a confirmation (feed/quote-posts). */
    val offersDelete: Boolean
        get() = isOwn && post.embed is PostEmbedView.Quote

    /** Report is offered on someone else's post, when a labeler is configured ([reportAvailable]). */
    fun offersReport(reportAvailable: Boolean): Boolean = !isOwn && reportAvailable

    /** The menu shows: Delete or Report is offered. */
    fun offersMenu(reportAvailable: Boolean): Boolean = offersDelete || offersReport(reportAvailable)

    /** The menu's one item: Delete quote on the viewer's own quote, Report otherwise. */
    fun menuItem(): AppText = AppText.named(if (offersDelete) "quote_delete" else "action_report")

    /** What the menu's item sends. */
    fun menuEvent(): PostEvent = if (offersDelete) PostEvent.Delete(post) else PostEvent.Report(post)

    override fun equals(other: Any?): Boolean =
        other is PostCardContent && other.post == post && other.viewerDid == viewerDid && other.reason == reason

    override fun hashCode(): Int = (post.hashCode() * 31 + viewerDid.hashCode()) * 31 + reason.hashCode()

    companion object {
        /** [post]'s card for the signed-in [viewerDid], in a timeline through [reason]'s repost when given. */
        fun of(post: PostView, viewerDid: String, reason: ReasonRepost? = null): PostCardContent =
            PostCardContent(post, isOwn = post.author.did.value == viewerDid, viewerDid = viewerDid, reason = reason)

        /** [reposted]'s card in a timeline, headed by who reposted it (feed/reposts). */
        fun ofRepost(reposted: RepostedPostView, viewerDid: String): PostCardContent = of(reposted.post, viewerDid, reposted.reason)

        /** The card's own words that aren't any one post's. */
        fun more(): AppText = AppText.named("post_card_more")

        fun open(): AppText = AppText.named("post_card_open")

        fun openPlace(): AppText = AppText.named("post_card_open_place")

        fun openReposter(): AppText = AppText.named("post_reposted_by_open")

        fun like(): AppText = AppText.named("like_content_description")

        fun quote(): AppText = AppText.named("quote_content_description")

        fun showQuotes(): AppText = AppText.named("post_show_quotes")

        private fun proposesName(name: String): AppText = AppText.named("post_card_proposes", AppText.written(name))

        private fun plural(name: String, count: Long): AppText =
            AppText.Plural(name, count.toInt(), persistentListOf(AppText.written(formatWhole(count))))

        private fun status(status: String): AppText =
            AppText.named(
                when (status) {
                    PostProposalStatus.OPEN -> "post_status_open"
                    PostProposalStatus.ADOPTED -> "post_status_adopted"
                    PostProposalStatus.REJECTED -> "post_status_rejected"
                    PostProposalStatus.EXPIRED -> "post_status_expired"
                    PostProposalStatus.SUPERSEDED -> "post_status_superseded"
                    else -> "post_status_closed"
                },
            )
    }
}
