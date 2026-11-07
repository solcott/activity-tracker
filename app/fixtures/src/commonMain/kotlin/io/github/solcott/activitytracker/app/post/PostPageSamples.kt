package io.github.solcott.activitytracker.app.post

import androidx.compose.foundation.text.input.TextFieldState
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.app.fixtures.fakeComment
import io.github.solcott.activitytracker.app.fixtures.fakePost
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.fixtures.fakeRepostedPost
import io.github.solcott.activitytracker.app.interactions.CommentThread
import io.github.solcott.activitytracker.app.interactions.CommentsPaging
import io.github.solcott.activitytracker.app.interactions.CommentsState
import io.github.solcott.activitytracker.app.interactions.LikesList
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.PostViewerState
import io.github.solcott.uistate.LoadStatus
import kotlinx.collections.immutable.persistentListOf

/** The post page (feed/post-page): Bob's Followers post as Alice sees it, liked by her, with Carol's comment and Bob's reply. */
object PostPageSamples {
    val post: PostView = fakePost().copy(likeCount = 3, commentCount = 2, repostCount = 1, viewer = PostViewerState())

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
                        draft = TextFieldState(),
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
                ("reposters" to reposters)

    /** The AppView doesn't serve the post to the viewer. */
    val notFound: PostPageState
        get() = PostPageState(notFound = true, eventSink = {})

    /** Who liked it, shown. */
    val likers: PostPageState
        get() =
            PostPageState(
                ContentStates.loaded(
                    page.copy(likesList = LikesList(persistentListOf(fakeProfileBasic(Fixed.CAROL)), isLoading = false, error = null))
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
}
