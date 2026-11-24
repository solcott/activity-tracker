package io.github.solcott.activitytracker.app.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.comments.CommentsScreen
import io.github.solcott.activitytracker.app.interactions.CommentsPreview
import io.github.solcott.activitytracker.app.interactions.CommentsPreviewEvent
import io.github.solcott.activitytracker.app.interactions.rememberCommentChanges
import io.github.solcott.activitytracker.app.interactions.rememberCommentCountMoved
import io.github.solcott.activitytracker.app.interactions.rememberComments
import io.github.solcott.activitytracker.app.interactions.rememberLikesList
import io.github.solcott.activitytracker.app.interactions.rememberRepostsList
import io.github.solcott.activitytracker.app.mapContent
import io.github.solcott.activitytracker.app.posts.PostEvent
import io.github.solcott.activitytracker.app.posts.rememberPostEventSink
import io.github.solcott.activitytracker.app.posts.rememberQuoting
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.report.blockReported
import io.github.solcott.activitytracker.app.report.rememberReporting
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.QuotePosts
import io.github.solcott.activitytracker.data.feed.withLikeOverrides
import io.github.solcott.activitytracker.data.feed.withQuoteOverrides
import io.github.solcott.activitytracker.data.feed.withRepostOverrides
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.segments.SegmentNames
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.LoadStatus
import io.github.solcott.uistate.circuit.produceRetainedContentState
import io.github.solcott.uistate.hasLoaded

/**
 * The post page (feed/post-page): the post as the viewer left it (their votes, likes, reposts and comment changes laid over it), its
 * comments block, who liked or reposted it once asked, and its quotes on their own screen ([PostQuotesScreen]); its comments are on their
 * own screen ([CommentsScreen]); the card's events are the timeline's ([rememberPostEventSink]), except that opening the post again does
 * nothing.
 */
@CircuitInject(PostPageScreen::class, SessionScope::class)
@Composable
@Suppress("LongParameterList") // Metro injects each repository the page reads or writes through.
fun PostPagePresenter(
    screen: PostPageScreen,
    navigator: Navigator,
    feed: FeedRepository,
    activities: ActivityRepository,
    profiles: ProfileRepository,
    names: SegmentNames,
    blocks: BlockRepository,
    reports: ReportRepository,
    quotes: QuotePosts,
): PostPageState {
    val uri = AtUri(screen.uri)
    var loadKey by rememberRetained { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var showLikes by remember { mutableStateOf(false) }
    var showReposts by remember { mutableStateOf(false) }
    val flights = rememberSingleFlight()

    val content = produceRetainedContentState<PostView?>(null, loadKey) { feed.postOutcome(uri) }
    val loaded = content.data
    // How far the viewer's comment actions on the Comments screen have moved the count since [loaded] loaded: a reload's answer counts
    // them itself.
    val moved = rememberCommentCountMoved(uri, loaded, feed.commentMoves)
    val nameChanges by names.changes.collectAsState()
    val likeOverrides by feed.likeOverrides.collectAsState()
    val repostOverrides by feed.localReposts.overrides.collectAsState()
    val quoteOverrides by feed.localQuotes.overrides.collectAsState()
    val post =
        remember(loaded, moved, nameChanges, likeOverrides, repostOverrides, quoteOverrides) {
            loaded?.let { view ->
                val shown =
                    names
                        .applyTo(view)
                        .withLikeOverrides(likeOverrides)
                        .withRepostOverrides(repostOverrides)
                        .withQuoteOverrides(quoteOverrides)
                if (moved == 0) shown else shown.copy(commentCount = (shown.commentCount + moved).coerceAtLeast(0))
            }
        }

    // The block's first page, loaded again after each comment action on the Comments screen.
    val comments = rememberComments(uri, profiles.did, loadKey + rememberCommentChanges(uri, feed.commentMoves), feed)
    val reporting = rememberReporting(reports) { author, _ -> flights.blockReported(author, blocks) { error = it } }
    // Deleting the page's own quote leaves the page, which would only say it's gone.
    val quoting = rememberQuoting(quotes, onDelete = { if (it.uri == uri) navigator.pop() }) { error = it }
    val postEvents = rememberPostEventSink(navigator, names, activities, reporting, quoting) { error = it }
    val likesList = rememberLikesList(showLikes, uri, viewerGave = post?.viewer?.like != null, feed, profiles)
    val repostsList = rememberRepostsList(showReposts, uri, viewerGave = post?.viewer?.repost != null, feed, profiles)

    val sink: (PostPageEvent) -> Unit = { event ->
        when (event) {
            PostPageEvent.Back -> navigator.pop()
            PostPageEvent.Retry -> loadKey++
            // Already here.
            is PostPageEvent.Post -> if (event.event !is PostEvent.OpenPost) postEvents(event.event)
            PostPageEvent.ToggleLikesList -> showLikes = !showLikes
            PostPageEvent.ToggleRepostsList -> showReposts = !showReposts
            is PostPageEvent.OpenProfile -> navigator.goTo(ProfileScreen(event.did.value))
            PostPageEvent.OpenQuotes -> navigator.goTo(PostQuotesScreen(screen.uri))
            is PostPageEvent.OpenComments -> navigator.goTo(CommentsScreen(screen.uri, focus = event.focus))
        }
    }
    val failure = (content.status as? LoadStatus.Failed)?.error
    val page = post?.let {
        PostPage(
            post = it,
            comments =
                CommentsPreview.of(
                    it.commentCount,
                    comments.paging.items,
                    comments.sent,
                    profiles.did,
                    loading = comments.paging.loadingFirstPage,
                ) { e ->
                    sink(PostPageEvent.OpenComments(focus = e == CommentsPreviewEvent.Compose))
                },
            viewerDid = profiles.did,
            likesList = likesList,
            repostsList = repostsList,
            error = error,
            reporting = reporting,
            quote = quoting.fields,
        )
    }
    return PostPageState(
        content = content.mapContent { page },
        notFound = !content.hasLoaded && failure is DataError.Api && failure.code == NOT_FOUND,
        eventSink = sink,
    )
}

private const val NOT_FOUND = "NotFound"
