package io.github.solcott.activitytracker.app.post

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.posts.PostCard
import io.github.solcott.activitytracker.app.posts.QuoteSheetHost
import io.github.solcott.activitytracker.app.report.ReportingHost
import io.github.solcott.activitytracker.app.ui.EmptyListText
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.pagedFooter
import io.github.solcott.activitytracker.app.ui.pagedRefreshStatus
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.post_quotes_empty
import io.github.solcott.activitytracker.composeApp.resources.post_quotes_title
import io.github.solcott.activitytracker.composeApp.resources.post_unavailable
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.stringResource

/** A post's quotes (feed/quote-posts) as post cards, newest first, a page at a time; once the post is gone for the viewer, saying so. */
@CircuitInject(PostQuotesScreen::class, SessionScope::class)
@Composable
fun PostQuotesUi(state: PostQuotesState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state.quotes.canLoadMore) { state.eventSink(PostQuotesEvent.LoadMore) }
    ListScreen(
        title = stringResource(Res.string.post_quotes_title),
        onBack = { state.eventSink(PostQuotesEvent.Back) },
        modifier = modifier,
        listState = listState,
    ) {
        state.error?.let { error -> item(key = "error") { ErrorText(error, Modifier.fillMaxWidth().padding(16.dp)) } }
        if (state.notFound) {
            item(key = "not-found") { EmptyListText(stringResource(Res.string.post_unavailable)) }
            return@ListScreen
        }
        pagedRefreshStatus(state.quotes, UiError.Kind.LoadQuotes) { state.eventSink(PostQuotesEvent.Retry) }
        items(state.quotes.items, key = { it.uri.value }) { post ->
            PostCard(
                post,
                state.viewerDid,
                state.reporting.available,
                onEvent = { state.eventSink(PostQuotesEvent.Post(it)) },
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        pagedFooter(state.quotes, UiError.Kind.LoadQuotes, onRetry = { state.eventSink(PostQuotesEvent.Retry) }) {
            EmptyListText(stringResource(Res.string.post_quotes_empty))
        }
    }
    ReportingHost(state.reporting)
    QuoteSheetHost(state.quote)
}
