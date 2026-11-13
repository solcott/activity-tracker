package io.github.solcott.activitytracker.app.ui

import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.fakeComment
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.interactions.CommentAction
import io.github.solcott.activitytracker.app.interactions.CommentConfirmation
import io.github.solcott.activitytracker.app.interactions.CommentEvent
import io.github.solcott.activitytracker.app.interactions.CommentRowKind
import io.github.solcott.activitytracker.app.interactions.CommentThread
import io.github.solcott.activitytracker.app.interactions.CommentsPaging
import io.github.solcott.activitytracker.app.interactions.CommentsState
import io.github.solcott.activitytracker.app.interactions.CommentsTexts
import io.github.solcott.activitytracker.app.interactions.RepliesState
import io.github.solcott.activitytracker.app.posts.PostCardContent
import io.github.solcott.activitytracker.app.posts.PostCardSamples
import io.github.solcott.activitytracker.app.posts.PostEvent
import io.github.solcott.activitytracker.app.posts.QuoteSheetTexts
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allPluralStringResources
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.feed.SentComment
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.uistate.LoadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** What a post card, the comment list and the quote sheet show, chosen once for both UIs (dev/shared-display-text). */
class PostCardContentTest {
    private fun card(post: PostView) = PostCardContent.of(post, PostCardSamples.viewer)

    private fun named(name: String, vararg args: AppText) = AppText.named(name, *args)

    private fun count(name: String, n: Int) = AppText.Plural(name, n, persistentListOf(AppText.written("$n")))

    @Test
    fun someoneElsesProposal() {
        val card = card(PostCardSamples.proposal)
        assertFalse(card.isOwn)
        assertEquals(AppText.written("Bob"), card.author())
        assertEquals(named("visibility_followers"), card.audience())
        assertEquals(named("post_card_proposes", AppText.written("Wiggly Woods")), card.proposes())
        assertEquals(
            named(
                "text_dot_pair",
                named("post_status_open"),
                named("segment_name_totals", AppText.written("4"), AppText.written("1")),
            ),
            card.proposalStatus(),
        )
        assertEquals("post_card_segment", (card.place() as AppText.Named).name)
        assertTrue(card.canVote)
        assertTrue(card.offersLike && card.liked && card.offersRepost && !card.reposted && card.offersQuote)
        assertEquals(
            named(
                "post_card_counts",
                count("like_count", 3),
                count("comments_count", 2),
                count("repost_count", 1),
                count("quote_count", 2),
            ),
            card.counts(),
        )
        assertFalse(card.offersMenu(reportAvailable = false))
        assertTrue(card.offersMenu(reportAvailable = true))
        assertEquals(named("action_report"), card.menuItem())
        assertNull(card.quoted)
        assertFalse(card.quotedUnavailable || card.embedUnknown)
    }

    @Test
    fun aVoteTapTogglesTheViewersOwnVoteOff() {
        val voted = card(PostCardSamples.all.getValue("voted"))
        assertTrue(voted.votedUp)
        assertEquals(PostEvent.Vote(voted.post, null), voted.vote(up = true))
        assertEquals(PostEvent.Vote(voted.post, SegmentNameVoteDirection.DOWN), voted.vote(up = false))
        assertTrue(card(PostCardSamples.votedDown).votedDown)
    }

    @Test
    fun ownPostsOfferNoLikeRepostOrMenuAndPendingOnesNoQuote() {
        val own = card(PostCardSamples.own)
        assertTrue(own.isOwn)
        assertFalse(own.offersLike || own.offersRepost || own.offersMenu(reportAvailable = true))
        assertTrue(own.offersQuote)
        assertFalse(card(PostCardSamples.pending).offersQuote)
    }

    @Test
    fun ownQuoteOffersDelete() {
        val card = card(PostCardSamples.ownQuote)
        assertTrue(card.offersDelete && card.offersMenu(reportAvailable = false))
        assertEquals(named("quote_delete"), card.menuItem())
        assertEquals(PostEvent.Delete(card.post), card.menuEvent())
    }

    @Test
    fun quotes() {
        val quote = card(PostCardSamples.quote)
        assertNotNull(quote.quoted)
        assertEquals(named("text_dot_pair", AppText.written("Bob"), named("visibility_followers")), quote.quotedHeader())
        assertEquals(named("post_card_proposes", AppText.written("Wiggly Woods")), quote.quotedProposes())
        assertNull(quote.quotedLink)
        assertNull(quote.proposal)

        val ofQuote = card(PostCardSamples.all.getValue("quoteOfQuote"))
        assertNotNull(ofQuote.quotedLink)
        assertNull(ofQuote.quotedProposes())

        val gone = card(PostCardSamples.all.getValue("unavailableQuote"))
        assertNull(gone.quoted)
        assertTrue(gone.quotedUnavailable)
        assertNull(gone.quotedHeader())

        assertTrue(card(PostCardSamples.unknown).embedUnknown)
    }

    @Test
    fun repostedBy() {
        val reposts = PostCardSamples.reposts
        fun by(name: String) = PostCardContent.ofRepost(reposts.getValue(name), PostCardSamples.viewer).repostedBy()
        assertEquals(named("post_reposted_by_you"), by("repostedByYou"))
        assertEquals(named("post_reposted_by", AppText.written("Bob")), by("repostedByOne"))
        assertEquals(
            AppText.Plural("post_reposted_by_others", 1, persistentListOf(AppText.written("Bob"), AppText.written("1"))),
            by("repostedByOthers"),
        )
        assertNull(card(PostCardSamples.proposal).repostedBy())
        assertTrue(card(reposts.getValue("repostedByYou").post).reposted)
    }

    private val carol = fakeProfileBasic(Fixed.CAROL)

    private fun comments(isOwn: Boolean, vararg threads: CommentThread) =
        CommentsState(
            paging = CommentsPaging(items = persistentListOf(*threads), refresh = LoadStatus.Idle, hasAnswer = true),
            sent = persistentListOf(),
            draft = textFieldStateOf(),
            replyingTo = null,
            viewerDid = Fixed.ALICE,
            author = if (isOwn) fakeProfileBasic(Fixed.ALICE) else fakeProfileBasic(Fixed.BOB),
            isOwn = isOwn,
            count = 2,
        )

    @Test
    fun someoneElsesCommentOnSomeoneElsesPost() {
        val comment = fakeComment("3kc1", carol)
        val state = comments(isOwn = false, CommentThread(comment.uri, comment, persistentListOf()))
        assertEquals(
            listOf(CommentAction.Reply, CommentAction.MuteAuthor, CommentAction.BlockAuthor, CommentAction.Report),
            CommentsTexts.actions(comment, state, reportAvailable = true).toList(),
        )
        val row = CommentsTexts.rows(state, reportAvailable = false).single()
        assertEquals(CommentRowKind.Comment, row.kind)
        assertTrue(row.offersReply)
        assertEquals(listOf(CommentAction.MuteAuthor, CommentAction.BlockAuthor), row.menu.map { it.action })
        assertTrue(row.menu.last().confirms && row.menu.last().destructive)
        assertEquals(CommentEvent.MuteAuthor(comment.uri), row.menu.first().event())
    }

    @Test
    fun aHiddenCommentOnTheViewersOwnPost() {
        val hidden = fakeComment("3kc2", carol).copy(hidden = true)
        val state = comments(isOwn = true, CommentThread(hidden.uri, hidden, persistentListOf()))
        val row = CommentsTexts.rows(state, reportAvailable = false).single()
        assertTrue(row.hidden)
        assertFalse(row.offersReply)
        assertEquals(named("comment_hidden"), row.hiddenNote)
        assertEquals(CommentAction.Unhide, row.menu.first().action)
        assertEquals(
            named("comment_hidden_by", AppText.written("Alice")),
            CommentsTexts.hiddenNote(state.copy(isOwn = false, author = fakeProfileBasic(Fixed.ALICE))),
        )
    }

    @Test
    fun rowsFollowTheThreads() {
        val root = fakeComment("3kc1", carol)
        val reply = fakeComment("3kc2", fakeProfileBasic(Fixed.ALICE))
        val sentReply = SentComment(AtUri.of(Fixed.ALICE, Nsid.FEED_COMMENT, "3ks1"), fakeProfileBasic(Fixed.ALICE), "Yes", Fixed.NOW)
        val sent = SentComment(AtUri.of(Fixed.ALICE, Nsid.FEED_COMMENT, "3ks2"), fakeProfileBasic(Fixed.ALICE), "Also", Fixed.NOW)
        val gone = AtUri.of(Fixed.BOB, Nsid.FEED_COMMENT, "3kgone")
        val state =
            comments(
                    isOwn = false,
                    CommentThread(root.uri, root, persistentListOf(reply), persistentListOf(sentReply), moreReplies = 3),
                    CommentThread(gone, null, persistentListOf(), repliesState = RepliesState.Loading, moreReplies = 1),
                )
                .copy(sent = persistentListOf(sent))
        val rows = CommentsTexts.rows(state, reportAvailable = false)
        assertEquals(
            listOf(
                CommentRowKind.Comment,
                CommentRowKind.Comment,
                CommentRowKind.Sent,
                CommentRowKind.MoreReplies,
                CommentRowKind.Unavailable,
                CommentRowKind.MoreReplies,
                CommentRowKind.Sent,
            ),
            rows.map { it.kind },
        )
        assertEquals(listOf(false, true, true, true, false, true, false), rows.map { it.isReply })
        assertEquals(listOf(CommentAction.Delete), rows[1].menu.map { it.action })
        assertEquals(CommentsTexts.moreReplies(3), rows[3].more)
        assertTrue(rows[5].repliesLoading)
        assertNull(rows[5].more)
        assertEquals(rows.size, rows.map { it.key }.distinct().size)
    }

    @Test
    fun onlyDeleteAndBlockAskFirst() {
        val carol = AppText.written("Carol")
        assertEquals(
            CommentConfirmation(
                AppText.named("delete_comment_title"),
                AppText.named("delete_comment_message"),
                AppText.named("detail_delete"),
            ),
            CommentsTexts.confirmation(CommentAction.Delete, carol),
        )
        assertEquals(
            CommentConfirmation(
                AppText.named("block_confirm_title", carol),
                AppText.named("block_confirm_message"),
                AppText.named("action_block"),
            ),
            CommentsTexts.confirmation(CommentAction.BlockAuthor, carol),
        )
        val asking = CommentAction.entries.filter { CommentsTexts.confirmation(it, carol) != null }
        assertEquals(listOf(CommentAction.Delete, CommentAction.BlockAuthor), asking)
        val menu = CommentsTexts.menu(CommentAction.entries.toSet(), AtUri("at://did:plc:carol/app.activitytracker.feed.comment/1"))
        assertEquals(menu.filter { it.confirms }.map { it.action }, asking, "the items that confirm are the ones with a confirmation")
    }

    @Test
    fun everyNameExists() {
        val cards =
            PostCardSamples.all.values.map(::card) +
                PostCardSamples.reposts.values.map { PostCardContent.of(it.post, PostCardSamples.viewer, it.reason) }
        val texts =
            cards.flatMap(::cardTexts) +
                listOf(
                    PostCardContent.more(),
                    PostCardContent.open(),
                    PostCardContent.openPlace(),
                    PostCardContent.openReposter(),
                    PostCardContent.like(),
                    PostCardContent.quote(),
                    PostCardContent.showQuotes(),
                ) +
                CommentAction.entries.map(CommentsTexts::label) +
                listOf(
                    CommentsTexts.title,
                    CommentsTexts.moreReplies(2),
                    CommentsTexts.unavailable,
                    CommentsTexts.cancelReply,
                    CommentsTexts.placeholder,
                    CommentsTexts.send,
                    CommentsTexts.more,
                    CommentsTexts.deleteTitle,
                    CommentsTexts.deleteMessage,
                    CommentsTexts.blockTitle(AppText.written("Carol")),
                    CommentsTexts.blockMessage,
                    CommentsTexts.blockConfirm,
                    CommentsTexts.cancel,
                    CommentsTexts.hiddenNote(comments(isOwn = true)),
                    CommentsTexts.hiddenNote(comments(isOwn = false)),
                    CommentsTexts.count(comments(isOwn = false)),
                ) +
                listOf(
                    QuoteSheetTexts.title,
                    QuoteSheetTexts.post,
                    QuoteSheetTexts.cancel,
                    QuoteSheetTexts.field,
                    QuoteSheetTexts.tooLong,
                    QuoteSheetTexts.publicOnly,
                    QuoteSheetTexts.audienceName,
                    QuoteSheetTexts.deleteTitle,
                    QuoteSheetTexts.deleteMessage,
                    QuoteSheetTexts.deleteConfirm,
                ) +
                Visibility.all.map(QuoteSheetTexts::audience)
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources || it in Res.allPluralStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun cardTexts(card: PostCardContent): List<AppText> =
        listOfNotNull(
            card.author(),
            card.audience(),
            card.repostedBy(),
            card.place(),
            card.proposes(),
            card.proposalStatus(),
            card.voteUp(),
            card.voteDown(),
            card.quotedUnavailableText(),
            card.quotedHeader(),
            card.quotedProposes(),
            card.quotedLinkText(),
            card.quotedOpen(),
            card.embedUnknownText(),
            card.repostLabel(),
            card.counts(),
            card.menuItem(),
        )

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Written,
            is AppText.Time -> emptyList()
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            is AppText.Plural -> listOf(text.name) + text.args.flatMap(::names)
        }
}
