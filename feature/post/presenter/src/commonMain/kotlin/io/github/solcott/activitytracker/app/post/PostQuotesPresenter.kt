package io.github.solcott.activitytracker.app.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.posts.rememberPostEventSink
import io.github.solcott.activitytracker.app.posts.rememberQuoting
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.report.rememberReporting
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
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
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import io.github.solcott.uistate.updated
import kotlinx.coroutines.flow.flow

/**
 * A post's quotes (feed/quote-posts): a page at a time from [FeedRepository.quotesPage], the viewer's own unlisted quotes of the post
 * first, with their likes, reposts, quotes and deletes this session laid over the pages already loaded. The cards' events are the
 * timeline's ([rememberPostEventSink]), with a quote sheet of the page's own.
 */
@CircuitInject(PostQuotesScreen::class, SessionScope::class)
@Composable
@Suppress("LongParameterList") // Metro injects each repository the page reads or writes through.
fun PostQuotesPresenter(
    screen: PostQuotesScreen,
    navigator: Navigator,
    feed: FeedRepository,
    activities: ActivityRepository,
    profiles: ProfileRepository,
    names: SegmentNames,
    blocks: BlockRepository,
    reports: ReportRepository,
    quotes: QuotePosts,
): PostQuotesState {
    val uri = AtUri(screen.uri)
    val page = rememberRetainedPaging(screen, idOf = PostView::uri) { cursor -> flow { emit(feed.quotesPage(uri, cursor)) } }
    var error by rememberRetained { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    val reporting =
        rememberReporting(reports) { author, _ ->
            flights.launch(BlockQuoteAuthor) {
                runSuspendCatching { blocks.setBlocked(author, true) }
                    .onSuccess { error = null }
                    .onFailure { error = uiErrorOf(UiError.Kind.UpdateBlock, it) }
            }
        }
    val quoting = rememberQuoting(quotes) { error = it }
    val postEvents = rememberPostEventSink(navigator, names, activities, reporting, quoting) { error = it }
    val nameChanges by names.changes.collectAsState()
    val localPosts by feed.localPosts.changes.collectAsState()
    val likeOverrides by feed.likeOverrides.collectAsState()
    val repostOverrides by feed.localReposts.overrides.collectAsState()
    val quoteOverrides by feed.localQuotes.overrides.collectAsState()
    val shown =
        remember(page.state, nameChanges, localPosts, likeOverrides, repostOverrides, quoteOverrides) {
            val removed = localPosts.filterValues { it == null }.keys
            page.state.updated(PostView::uri) { items ->
                items
                    .filterNot { it.uri.value in removed }
                    .map {
                        names
                            .applyTo(it)
                            .withLikeOverrides(likeOverrides)
                            .withRepostOverrides(repostOverrides)
                            .withQuoteOverrides(quoteOverrides)
                    }
            }
        }
    val failure = (page.state.refresh as? LoadStatus.Failed)?.error
    return PostQuotesState(
        quotes = shown,
        notFound = shown.items.isEmpty() && failure is DataError.Api && failure.code == QUOTED_NOT_FOUND,
        viewerDid = profiles.did.value,
        error = error,
        reporting = reporting,
        quote = quoting.fields,
    ) { event ->
        when (event) {
            PostQuotesEvent.Back -> navigator.pop()
            PostQuotesEvent.LoadMore -> page.loadMore()
            PostQuotesEvent.Retry -> page.retry()
            is PostQuotesEvent.Post -> postEvents(event.event)
        }
    }
}

/** The single-flight key of blocking a quote's author from the report sheet. */
private object BlockQuoteAuthor

private const val QUOTED_NOT_FOUND = "NotFound"
