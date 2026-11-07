package io.github.solcott.activitytracker.app.post

import androidx.compose.foundation.text.input.rememberTextFieldState
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
import io.github.solcott.activitytracker.app.interactions.CommentActions
import io.github.solcott.activitytracker.app.interactions.CommentEvent
import io.github.solcott.activitytracker.app.interactions.CommentsState
import io.github.solcott.activitytracker.app.interactions.rememberComments
import io.github.solcott.activitytracker.app.interactions.rememberLikesList
import io.github.solcott.activitytracker.app.interactions.rememberRepostsList
import io.github.solcott.activitytracker.app.mapContent
import io.github.solcott.activitytracker.app.posts.PostEvent
import io.github.solcott.activitytracker.app.posts.rememberPostEventSink
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.report.rememberReporting
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.asSubject
import io.github.solcott.activitytracker.data.feed.withLikeOverrides
import io.github.solcott.activitytracker.data.feed.withRepostOverrides
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.segments.SegmentNames
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.LoadStatus
import io.github.solcott.uistate.circuit.produceRetainedContentState
import io.github.solcott.uistate.hasLoaded

/**
 * The post page (feed/post-page): the post as the viewer left it (their votes, likes, reposts and comment changes laid over it), its
 * comment threads a page at a time, and who liked or reposted it once asked. Comment actions are the activity page's ([CommentActions]);
 * the card's events are the timeline's ([rememberPostEventSink]), except that opening the post again does nothing.
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
): PostPageState {
    val uri = AtUri(screen.uri)
    var loadKey by rememberRetained { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var commentError by remember { mutableStateOf<UiError?>(null) }
    var showLikes by remember { mutableStateOf(false) }
    var showReposts by remember { mutableStateOf(false) }
    val draft = rememberTextFieldState()
    val replying = remember { mutableStateOf<CommentView?>(null) }
    val flights = rememberSingleFlight()

    val content = produceRetainedContentState<PostView?>(null, loadKey) { feed.postOutcome(uri) }
    val loaded = content.data
    // How far the viewer's comment actions have moved the count since [loaded] loaded: a reload's answer counts them itself.
    var moves by rememberRetained { mutableStateOf<CountMoves?>(null) }
    val nameChanges by names.changes.collectAsState()
    val likeOverrides by feed.likeOverrides.collectAsState()
    val repostOverrides by feed.localReposts.overrides.collectAsState()
    val post =
        remember(loaded, moves, nameChanges, likeOverrides, repostOverrides) {
            loaded?.let { view ->
                val shown = names.applyTo(view).withLikeOverrides(likeOverrides).withRepostOverrides(repostOverrides)
                val moved = moves.over(view)
                if (moved == 0) shown else shown.copy(commentCount = (shown.commentCount + moved).coerceAtLeast(0))
            }
        }

    val comments = rememberComments(uri, profiles.did, loadKey, feed)
    val commentActions =
        CommentActions(draft, flights, activities, blocks, comments, replying, { commentError = it }) { moved ->
            moves = loaded?.let { CountMoves(it, moves.over(it) + moved) }
        }
    val reporting =
        rememberReporting(reports) { author, target ->
            val current = post
            if (target is ReportTarget.Comment && current != null) {
                commentActions.on(CommentEvent.BlockAuthor(target.uri), current.asSubject())
            } else {
                flights.launch(BlockReportedAuthor) {
                    runSuspendCatching { blocks.setBlocked(author, true) }
                        .onSuccess { error = null }
                        .onFailure { error = uiErrorOf(UiError.Kind.UpdateBlock, it) }
                }
            }
        }
    val postEvents = rememberPostEventSink(navigator, names, activities, reporting) { error = it }
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
        }
    }
    val failure = (content.status as? LoadStatus.Failed)?.error
    val page = post?.let {
        PostPage(
            post = it,
            comments =
                CommentsState(
                    paging = comments.paging,
                    sent = comments.sent,
                    draft = draft,
                    replyingTo = replying.value,
                    viewerDid = profiles.did,
                    author = it.author,
                    isOwn = it.author.did == profiles.did,
                    count = it.commentCount,
                    error = commentError,
                    eventSink = { e -> commentActions.on(e, it.asSubject()) },
                ),
            viewerDid = profiles.did,
            likesList = likesList,
            repostsList = repostsList,
            error = error,
            reporting = reporting,
        )
    }
    return PostPageState(
        content = content.mapContent { page },
        notFound = !content.hasLoaded && failure is DataError.Api && failure.code == NOT_FOUND,
        eventSink = sink,
    )
}

/** How far the viewer's comment actions moved [base]'s count: [base] is the very view loaded, so a reload's answer starts again. */
private class CountMoves(val base: PostView, val moves: Int)

private fun CountMoves?.over(view: PostView): Int = this?.takeIf { it.base === view }?.moves ?: 0

/** The single-flight key of blocking the post's author from the report sheet. */
private object BlockReportedAuthor

private const val NOT_FOUND = "NotFound"
