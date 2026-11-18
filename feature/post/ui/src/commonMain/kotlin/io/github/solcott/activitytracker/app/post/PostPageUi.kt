package io.github.solcott.activitytracker.app.post

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.detail.CARD_PADDING
import io.github.solcott.activitytracker.app.interactions.LikersList
import io.github.solcott.activitytracker.app.interactions.LoadMoreNearTheEnd
import io.github.solcott.activitytracker.app.interactions.comments
import io.github.solcott.activitytracker.app.posts.PostCard
import io.github.solcott.activitytracker.app.posts.QuoteSheetHost
import io.github.solcott.activitytracker.app.report.ReportingHost
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.post_page_title
import io.github.solcott.activitytracker.composeApp.resources.post_show_quotes
import io.github.solcott.activitytracker.composeApp.resources.post_unavailable
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.uistate.hasLoaded
import org.jetbrains.compose.resources.stringResource

/**
 * The post page (feed/post-page): the post's card, Show likes and who liked it, then its comment threads and the field to add one, a page
 * at a time as the viewer scrolls. A post the AppView doesn't serve the viewer says it isn't available.
 */
@CircuitInject(PostPageScreen::class, SessionScope::class)
@Composable
fun PostPageUi(state: PostPageState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    // The page once it has loaded and while it's there: the placeholder before the first load is null.
    val page = state.content.data.takeIf { state.content.hasLoaded && !state.notFound }
    page?.let { LoadMoreNearTheEnd(listState, it.comments) }
    val retry = { state.eventSink(PostPageEvent.Retry) }
    ListScreen(
        title = stringResource(Res.string.post_page_title),
        onBack = { state.eventSink(PostPageEvent.Back) },
        content = state.content,
        errorKind = UiError.Kind.LoadPost,
        onRetry = retry,
        modifier = modifier,
        listState = listState,
        verticalPadding = 12.dp,
        notFound = if (state.notFound) stringResource(Res.string.post_unavailable) else null,
    ) { loaded ->
        pageItems(state, loaded, retry)
    }
    page?.let {
        ReportingHost(it.reporting)
        QuoteSheetHost(it.quote)
    }
}

private fun LazyListScope.pageItems(state: PostPageState, page: PostPage, onRetry: () -> Unit) {
    item(key = "refresh") { RefreshStatus(state.content, UiError.Kind.LoadPost, onRetry) }
    item(key = "post") {
        PostCard(
            page.post,
            viewerDid = page.viewerDid.value,
            canReport = page.reporting.available,
            onEvent = { state.eventSink(PostPageEvent.Post(it)) },
            modifier = Modifier.padding(horizontal = CARD_PADDING),
            onQuoteCount = { state.eventSink(PostPageEvent.OpenQuotes) },
        )
    }
    page.error?.let { error -> item(key = "error") { ErrorText(error, Modifier.padding(horizontal = CARD_PADDING)) } }
    item(key = "likes") {
        if (page.showsLikesToggle) {
            AppTextButton(
                onClick = { state.eventSink(PostPageEvent.ToggleLikesList) },
                modifier = Modifier.padding(horizontal = CARD_PADDING),
            ) {
                Text(PostPageTexts.likesToggle(shown = page.likesList != null).string())
            }
        }
    }
    page.likesList?.let { list ->
        item(key = "likers") {
            LikersList(list, { state.eventSink(PostPageEvent.OpenProfile(it)) }, Modifier.padding(horizontal = CARD_PADDING))
        }
    }
    item(key = "reposts") {
        if (page.showsRepostsToggle) {
            AppTextButton(
                onClick = { state.eventSink(PostPageEvent.ToggleRepostsList) },
                modifier = Modifier.padding(horizontal = CARD_PADDING),
            ) {
                Text(PostPageTexts.repostsToggle(shown = page.repostsList != null).string())
            }
        }
    }
    page.repostsList?.let { list ->
        item(key = "reposters") {
            LikersList(list, { state.eventSink(PostPageEvent.OpenProfile(it)) }, Modifier.padding(horizontal = CARD_PADDING))
        }
    }
    item(key = "quotes") {
        if (page.showsQuotes) {
            AppTextButton(
                onClick = { state.eventSink(PostPageEvent.OpenQuotes) },
                modifier = Modifier.padding(horizontal = CARD_PADDING),
            ) {
                Text(stringResource(Res.string.post_show_quotes))
            }
        }
    }
    comments(page.comments, page.reporting)
}
