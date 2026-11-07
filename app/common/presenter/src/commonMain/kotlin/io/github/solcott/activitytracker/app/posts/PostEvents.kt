package io.github.solcott.activitytracker.app.posts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.activitylist.interactionErrorOf
import io.github.solcott.activitytracker.app.divesite.DiveSiteScreen
import io.github.solcott.activitytracker.app.post.PostPageScreen
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.report.Reporting
import io.github.solcott.activitytracker.app.report.ReportingEvent
import io.github.solcott.activitytracker.app.segment.SegmentScreen
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.feed.asSubject
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.data.segments.SegmentNames
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView

/**
 * Handles [PostEvent]s for a host presenter (feed/posts): opens navigate, a vote goes through [SegmentNames] and a like or repost
 * (feed/reposts) through [activities], each once per post at a time, and Report opens the host's [reporting] sheet. A vote, like or repost
 * that fails to queue goes to [onError]: a session that can't like or repost a non-public post is asked to sign in again.
 */
@Composable
fun rememberPostEventSink(
    navigator: Navigator,
    names: SegmentNames,
    activities: ActivityRepository,
    reporting: Reporting,
    onError: (UiError) -> Unit,
): (PostEvent) -> Unit {
    val flights = rememberSingleFlight()
    val currentOnError by rememberUpdatedState(onError)
    val currentReporting by rememberUpdatedState(reporting)
    return remember(navigator, names, activities, flights) {
        { event ->
            when (event) {
                is PostEvent.Open -> {
                    val embed = event.post.embed as? PostEmbedView.Proposal
                    embed?.segment?.let { navigator.goTo(SegmentScreen(it.uri.value)) }
                        ?: embed?.diveSite?.let { navigator.goTo(DiveSiteScreen(it.uri.value)) }
                }

                is PostEvent.OpenPost -> navigator.goTo(PostPageScreen(event.post.uri.value))

                is PostEvent.OpenAuthor -> navigator.goTo(ProfileScreen(event.post.author.did.value))

                is PostEvent.OpenReposter -> navigator.goTo(ProfileScreen(event.reposter.did.value))

                is PostEvent.Like -> {
                    flights.launch(PostLike(event.post.uri)) {
                        runSuspendCatching { activities.toggleLike(event.post.asSubject()) }
                            .onFailure { e -> interactionErrorOf(e)?.let(currentOnError) }
                    }
                }

                is PostEvent.Repost -> {
                    flights.launch(PostRepost(event.post.uri)) {
                        runSuspendCatching { activities.toggleRepost(event.post) }
                            .onFailure { e -> interactionErrorOf(e)?.let(currentOnError) }
                    }
                }

                is PostEvent.Vote -> {
                    flights.launch(event.post.uri) {
                        runSuspendCatching { names.vote(event.post, event.direction) }
                            .onFailure { currentOnError(uiErrorOf(UiError.Kind.VoteSegmentName, it)) }
                    }
                }

                is PostEvent.Report -> {
                    val post = event.post
                    currentReporting.eventSink(
                        ReportingEvent.Open(ReportTarget.Post(post.uri, post.cid, post.author.did), post.author.shownName)
                    )
                }
            }
        }
    }
}

/** The single-flight key of liking [post], apart from voting on it. */
private data class PostLike(val post: AtUri)

/** The single-flight key of reposting [post], apart from liking or voting on it. */
private data class PostRepost(val post: AtUri)
