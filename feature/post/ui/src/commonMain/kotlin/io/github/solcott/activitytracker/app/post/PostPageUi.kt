package io.github.solcott.activitytracker.app.post

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.detail.CARD_PADDING
import io.github.solcott.activitytracker.app.interactions.LikersList
import io.github.solcott.activitytracker.app.interactions.LoadMoreNearTheEnd
import io.github.solcott.activitytracker.app.interactions.comments
import io.github.solcott.activitytracker.app.posts.PostCard
import io.github.solcott.activitytracker.app.report.ReportingHost
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.detail_hide_likes
import io.github.solcott.activitytracker.composeApp.resources.detail_show_likes
import io.github.solcott.activitytracker.composeApp.resources.post_hide_reposts
import io.github.solcott.activitytracker.composeApp.resources.post_page_title
import io.github.solcott.activitytracker.composeApp.resources.post_show_reposts
import io.github.solcott.activitytracker.composeApp.resources.post_unavailable
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.stringResource

/**
 * The post page (feed/post-page): the post's card, Show likes and who liked it, then its comment threads and the field to add one, a page
 * at a time as the viewer scrolls. A post the AppView doesn't serve the viewer says it isn't available.
 */
@CircuitInject(PostPageScreen::class, SessionScope::class)
@Composable
fun PostPageUi(state: PostPageState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.post_page_title),
                onBack = { state.eventSink(PostPageEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (state.notFound) {
            Text(
                stringResource(Res.string.post_unavailable),
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AppScaffold
        }
        val retry = { state.eventSink(PostPageEvent.Retry) }
        ContentStateLayout(state.content, UiError.Kind.LoadPost, onRetry = retry, contentPadding = padding) { loaded ->
            // Loaded content is never null; the placeholder before the first load is.
            if (loaded != null) Loaded(state, loaded, padding, retry)
        }
    }
}

@Composable
private fun Loaded(state: PostPageState, page: PostPage, padding: PaddingValues, onRetry: () -> Unit) {
    val listState = rememberLazyListState()
    LoadMoreNearTheEnd(listState, page.comments)
    ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding + PaddingValues(vertical = 12.dp)) {
            item(key = "refresh") { RefreshStatus(state.content, UiError.Kind.LoadPost, onRetry) }
            item(key = "post") {
                PostCard(
                    page.post,
                    viewerDid = page.viewerDid.value,
                    canReport = page.reporting.available,
                    onEvent = { state.eventSink(PostPageEvent.Post(it)) },
                    modifier = Modifier.padding(horizontal = CARD_PADDING),
                )
            }
            page.error?.let { error -> item(key = "error") { ErrorText(error, Modifier.padding(horizontal = CARD_PADDING)) } }
            item(key = "likes") {
                if (page.post.likeCount > 0 || page.likesList != null) {
                    AppTextButton(
                        onClick = { state.eventSink(PostPageEvent.ToggleLikesList) },
                        modifier = Modifier.padding(horizontal = CARD_PADDING),
                    ) {
                        Text(stringResource(if (page.likesList != null) Res.string.detail_hide_likes else Res.string.detail_show_likes))
                    }
                }
            }
            page.likesList?.let { list ->
                item(key = "likers") {
                    LikersList(list, { state.eventSink(PostPageEvent.OpenProfile(it)) }, Modifier.padding(horizontal = CARD_PADDING))
                }
            }
            item(key = "reposts") {
                if (page.post.repostCount > 0 || page.repostsList != null) {
                    AppTextButton(
                        onClick = { state.eventSink(PostPageEvent.ToggleRepostsList) },
                        modifier = Modifier.padding(horizontal = CARD_PADDING),
                    ) {
                        Text(stringResource(if (page.repostsList != null) Res.string.post_hide_reposts else Res.string.post_show_reposts))
                    }
                }
            }
            page.repostsList?.let { list ->
                item(key = "reposters") {
                    LikersList(list, { state.eventSink(PostPageEvent.OpenProfile(it)) }, Modifier.padding(horizontal = CARD_PADDING))
                }
            }
            comments(page.comments, page.reporting)
        }
    }
    ReportingHost(page.reporting)
}
