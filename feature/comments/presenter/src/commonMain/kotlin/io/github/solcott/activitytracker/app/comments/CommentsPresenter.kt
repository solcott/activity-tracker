package io.github.solcott.activitytracker.app.comments

import androidx.compose.runtime.Composable
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
import io.github.solcott.activitytracker.app.interactions.rememberCommentCountMoved
import io.github.solcott.activitytracker.app.interactions.rememberComments
import io.github.solcott.activitytracker.app.launchAction
import io.github.solcott.activitytracker.app.mapContent
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.report.rememberReporting
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.InteractionSubject
import io.github.solcott.activitytracker.data.feed.asSubject
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.textfield.rememberTextFieldState
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.coroutines.flow.map

/**
 * A subject's comments (feed/comments), an activity's or a post's by [CommentsScreen.subject]'s collection: the subject as the viewer left
 * it, for its count, its author's hides and a reply's audience; its threads a page at a time with their replies; the field at the foot; and
 * the comment actions the pages had ([CommentActions]). Each action moves the subject's count wherever it's shown, through
 * [FeedRepository.commentMoves], so the page it was opened from shows it on return.
 */
@CircuitInject(CommentsScreen::class, SessionScope::class)
@Composable
@Suppress("LongParameterList") // Injected collaborators.
fun CommentsPresenter(
    screen: CommentsScreen,
    navigator: Navigator,
    feed: FeedRepository,
    activities: ActivityRepository,
    profiles: ProfileRepository,
    blocks: BlockRepository,
    reports: ReportRepository,
): CommentsScreenState {
    val uri = AtUri(screen.subject)
    var loadKey by rememberRetained { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val draft = rememberTextFieldState()
    val replying = remember { mutableStateOf<CommentView?>(null) }
    val flights = rememberSingleFlight()

    // Retained, so coming back from a profile shows the comments at once; a retry restarts it under them.
    val onPost = uri.collection == Nsid.FEED_POST
    val content =
        produceRetainedContentState<Subject?>(null, loadKey) {
            if (onPost) feed.postOutcome(uri).map { it.mapData(Subject::of) } else feed.activityOutcome(uri).map { it.mapData(Subject::of) }
        }
    val subject = content.data
    val moved = rememberCommentCountMoved(uri, subject, feed.commentMoves)
    val comments = rememberComments(uri, profiles.did, loadKey, feed)
    val actions =
        CommentActions(draft, flights, activities, blocks, comments, replying, { error = it }) { by -> feed.commentMoves.move(uri, by) }
    val reporting =
        rememberReporting(reports) { author, target ->
            val current = subject
            if (target is ReportTarget.Comment && current != null) {
                actions.on(CommentEvent.BlockAuthor(target.uri), current.interaction)
            } else {
                flights.launchAction(BlockReportedAuthor, UiError.Kind.UpdateBlock, { error = it }) {
                    blocks.setBlocked(author, true)
                    error = null
                }
            }
        }

    val sink: (CommentsScreenEvent) -> Unit = { event ->
        when (event) {
            CommentsScreenEvent.Back -> navigator.pop()
            CommentsScreenEvent.Retry -> loadKey++
        }
    }
    val page = subject?.let {
        CommentsPage(
            comments =
                CommentsState(
                    paging = comments.paging,
                    sent = comments.sent,
                    draft = draft,
                    replyingTo = replying.value,
                    viewerDid = profiles.did,
                    author = it.author,
                    isOwn = it.author.did == profiles.did,
                    count = (it.count + moved).coerceAtLeast(0),
                    error = error,
                    eventSink = { e -> actions.on(e, it.interaction) },
                ),
            subtitle = it.title?.let(AppText::written),
        )
    }
    return CommentsScreenState(
        content = content.mapContent { page },
        reporting = reporting,
        focus = screen.focus,
        onPost = onPost,
        eventSink = sink,
    )
}

/**
 * What the Comments screen needs of its subject: how to comment on it ([interaction], whose visibility caps a reply's audience), its
 * [author], whose hides count, its comment [count] as the viewer left it, and its [title], if it has one.
 */
private class Subject(val interaction: InteractionSubject, val author: ProfileViewBasic, val count: Long, val title: String?) {
    companion object {
        fun of(activity: ActivityView) =
            Subject(activity.asSubject(), activity.author, activity.commentCount, activity.record.title?.takeIf { it.isNotBlank() })

        fun of(post: PostView) = Subject(post.asSubject(), post.author, post.commentCount, title = null)
    }
}

/** The single-flight key of blocking a reported account from the report sheet. */
private object BlockReportedAuthor
