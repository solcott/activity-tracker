package io.github.solcott.activitytracker.app.post

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.app.fixtures.fakeComment
import io.github.solcott.activitytracker.app.fixtures.fakePost
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.fixtures.fakeQuote
import io.github.solcott.activitytracker.app.fixtures.fakeRepostedPost
import io.github.solcott.activitytracker.app.interactions.CommentThread
import io.github.solcott.activitytracker.app.interactions.CommentsPaging
import io.github.solcott.activitytracker.app.interactions.CommentsState
import io.github.solcott.activitytracker.app.interactions.LikesList
import io.github.solcott.activitytracker.app.interactions.RepliesState
import io.github.solcott.activitytracker.app.posts.QuoteFields
import io.github.solcott.activitytracker.app.posts.QuoteSheetSamples
import io.github.solcott.activitytracker.data.feed.SentComment
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.PostViewerState
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.LoadStatus
import kotlinx.collections.immutable.persistentListOf

/** The post page (feed/post-page): Bob's Followers post as Alice sees it, liked by her, with Carol's comment and Bob's reply. */
object PostPageSamples {
    val post: PostView = fakePost().copy(likeCount = 3, commentCount = 2, repostCount = 1, quoteCount = 2, viewer = PostViewerState())

    /** The page with one thread; a new draft each time. */
    val page: PostPage
        get() {
            val carols = fakeComment("3kc1", fakeProfileBasic(Fixed.CAROL), "The locals have called it that for years.")
            val bobs = fakeComment("3kc2", fakeProfileBasic(Fixed.BOB), "Exactly why I proposed it.")
            return PostPage(
                post = post,
                comments =
                    CommentsState(
                        paging =
                            CommentsPaging(
                                items = persistentListOf(CommentThread(carols.uri, carols, persistentListOf(bobs))),
                                refresh = LoadStatus.Idle,
                                hasAnswer = true,
                                endReached = true,
                            ),
                        sent = persistentListOf(),
                        draft = textFieldStateOf(),
                        replyingTo = null,
                        viewerDid = Fixed.ALICE,
                        author = post.author,
                        isOwn = false,
                        count = post.commentCount,
                    ),
                viewerDid = Fixed.ALICE,
            )
        }

    val all: Map<String, PostPageState>
        get() =
            contentSamples<PostPage?, PostPageState>(null, page) { PostPageState(it, eventSink = {}) } +
                ("notFound" to notFound) +
                ("reposters" to reposters) +
                ("ownQuote" to ownQuote)

    /** The AppView doesn't serve the post to the viewer. */
    val notFound: PostPageState
        get() = PostPageState(notFound = true, eventSink = {})

    /** Who liked it, shown. */
    val likers: PostPageState
        get() =
            PostPageState(
                ContentStates.loaded(
                    page.copy(likesList = LikesList(persistentListOf(fakeProfileBasic(Fixed.CAROL)), isLoading = false, error = null)),
                ),
                eventSink = {},
            )

    /** Who reposted it, shown, after Alice reposted it too (feed/reposts). */
    val reposters: PostPageState
        get() {
            val reposted =
                post.copy(repostCount = 2, viewer = PostViewerState(repost = fakeRepostedPost(reposted = true).post.viewer?.repost))
            val list =
                LikesList(persistentListOf(fakeProfileBasic(Fixed.ALICE), fakeProfileBasic(Fixed.CAROL)), isLoading = false, error = null)
            return PostPageState(ContentStates.loaded(page.copy(post = reposted, repostsList = list)), eventSink = {})
        }

    /** Alice's own quote of Bob's post, which offers Delete in its menu (feed/quote-posts). */
    val ownQuote: PostPageState
        get() =
            PostPageState(
                ContentStates.loaded(page.copy(post = fakeQuote(author = Fixed.ALICE, quoteCount = 1))),
                eventSink = {},
            )

    /**
     * What [all]'s content-loading states don't show (ios-native-post-card): who liked it, a failed vote, a comment Alice hid on her own
     * post, a reply in progress with sent comments, a thread with more replies and one whose replies failed, a failed comments page, the
     * quote sheet open and the delete confirmation.
     */
    val variants: Map<String, PostPageState>
        get() {
            fun loaded(page: PostPage) = PostPageState(ContentStates.loaded(page), eventSink = {})
            val base = page
            val carols = fakeComment("3kc1", fakeProfileBasic(Fixed.CAROL), "The locals have called it that for years.")
            val hidden = fakeComment("3kc3", fakeProfileBasic(Fixed.BOB), "This name is silly.").copy(hidden = true)
            val own = fakePost(author = Fixed.ALICE, rkey = "3kown", canVote = false).copy(commentCount = 1, viewer = PostViewerState())
            val sent = SentComment(AtUri.of(Fixed.ALICE, Nsid.FEED_COMMENT, "3ksent"), fakeProfileBasic(Fixed.ALICE), "Agreed!", Fixed.NOW)
            val reply =
                SentComment(AtUri.of(Fixed.ALICE, Nsid.FEED_COMMENT, "3ksentreply"), sent.author, "Me too.", Fixed.NOW, root = carols.uri)
            return mapOf(
                "likers" to likers,
                "actionFailed" to loaded(base.copy(error = UiError(UiError.Kind.VoteSegmentName))),
                "ownHidden" to
                    loaded(
                        base.copy(
                            post = own,
                            comments =
                                base.comments.copy(
                                    paging =
                                        base.comments.paging.copy(
                                            items = persistentListOf(CommentThread(hidden.uri, hidden, persistentListOf())),
                                        ),
                                    author = own.author,
                                    isOwn = true,
                                    count = 0,
                                ),
                        ),
                    ),
                "replying" to
                    loaded(
                        base.copy(
                            comments =
                                base.comments.copy(
                                    paging =
                                        base.comments.paging.copy(
                                            items =
                                                persistentListOf(
                                                    base.comments.paging.items.first().copy(sentReplies = persistentListOf(reply)),
                                                ),
                                        ),
                                    sent = persistentListOf(sent),
                                    draft = textFieldStateOf("Can't wait to ride it"),
                                    replyingTo = carols,
                                ),
                        ),
                    ),
                "moreReplies" to
                    loaded(
                        base.copy(
                            comments =
                                base.comments.copy(
                                    paging =
                                        base.comments.paging.copy(
                                            items =
                                                persistentListOf(
                                                    base.comments.paging.items.first().copy(moreReplies = 3),
                                                    CommentThread(
                                                        AtUri.of(Fixed.BOB, Nsid.FEED_COMMENT, "3kgone"),
                                                        null,
                                                        persistentListOf(fakeComment("3kc4", fakeProfileBasic(Fixed.CAROL), "Still true.")),
                                                        moreReplies = 2,
                                                        repliesState = RepliesState.Failed(UiError(UiError.Kind.LoadComments)),
                                                    ),
                                                ),
                                        ),
                                ),
                        ),
                    ),
                "commentsFailed" to
                    loaded(
                        base.copy(
                            comments =
                                base.comments.copy(paging = base.comments.paging.copy(append = LoadStatus.Failed(DataError.Network))),
                        ),
                    ),
                "quoting" to loaded(base.copy(quote = QuoteSheetSamples.open)),
                "confirmDelete" to
                    fakeQuote(author = Fixed.ALICE, quoteCount = 1).let { quote ->
                        loaded(base.copy(post = quote, quote = QuoteFields(confirmDelete = quote)))
                    },
            )
        }
}
