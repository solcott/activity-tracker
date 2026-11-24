package io.github.solcott.activitytracker.app.post

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.detail.CARD_PADDING
import io.github.solcott.activitytracker.app.interactions.CommentCount
import io.github.solcott.activitytracker.app.interactions.CommentsPreviewBlock
import io.github.solcott.activitytracker.app.interactions.LikersList
import io.github.solcott.activitytracker.app.interactions.SocialCount
import io.github.solcott.activitytracker.app.interactions.SocialCountsRow
import io.github.solcott.activitytracker.app.posts.PostCard
import io.github.solcott.activitytracker.app.posts.QuoteSheetHost
import io.github.solcott.activitytracker.app.report.ReportingHost
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.errorItem
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.post_page_title
import io.github.solcott.activitytracker.composeApp.resources.post_unavailable
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.uistate.hasLoaded
import org.jetbrains.compose.resources.stringResource

/**
 * The post page (feed/post-page): the post's card, then one card of what people did with it: likes, reposts and quotes, and its comments in
 * brief, which open the Comments screen. A post the AppView doesn't serve the viewer says it isn't available.
 */
@CircuitInject(PostPageScreen::class, SessionScope::class)
@Composable
fun PostPageUi(state: PostPageState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    // The page once it has loaded and while it's there: the placeholder before the first load is null.
    val page = state.content.data.takeIf { state.content.hasLoaded && !state.notFound }
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
            showCounts = false,
        )
    }
    errorItem(page.error)
    item(key = "social") { Social(state, page, Modifier.padding(horizontal = CARD_PADDING).padding(top = 12.dp)) }
}

/**
 * Everything social about the post in one card under it: its like, repost, quote and comment counts on one line (the first two show who
 * liked and reposted it, the quotes open its quotes once there are any), with the lists once shown, then, over a hairline, its comments in
 * brief (feed/comments), which open the Comments screen. Liking, reposting and quoting stay on the post's card.
 */
@Composable
private fun Social(state: PostPageState, page: PostPage, modifier: Modifier = Modifier) {
    val inset = Modifier.padding(horizontal = 12.dp)
    val post = page.post
    AppCard(modifier) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            SocialCountsRow {
                SocialCount(
                    AppIcon.Like,
                    PostPageTexts.number(post.likeCount),
                    PostPageTexts.count("like_count", post.likeCount).string(),
                    onClickLabel = PostPageTexts.likesToggle(shown = page.likesList != null).string(),
                    onClick = if (page.showsLikesToggle) ({ state.eventSink(PostPageEvent.ToggleLikesList) }) else null,
                )
                SocialCount(
                    AppIcon.Repost,
                    PostPageTexts.number(post.repostCount),
                    PostPageTexts.count("repost_count", post.repostCount).string(),
                    onClickLabel = PostPageTexts.repostsToggle(shown = page.repostsList != null).string(),
                    onClick = if (page.showsRepostsToggle) ({ state.eventSink(PostPageEvent.ToggleRepostsList) }) else null,
                )
                SocialCount(
                    AppIcon.Quote,
                    PostPageTexts.number(post.quoteCount),
                    PostPageTexts.count("quote_count", post.quoteCount).string(),
                    modifier = Modifier.testTag(TestTags.POST_QUOTE_COUNT),
                    onClickLabel = PostPageTexts.showQuotes.string(),
                    onClick = if (page.showsQuotes) ({ state.eventSink(PostPageEvent.OpenQuotes) }) else null,
                )
                CommentCount(page.comments)
            }
            page.likesList?.let { list -> LikersList(list, { state.eventSink(PostPageEvent.OpenProfile(it)) }, inset) }
            page.repostsList?.let { list -> LikersList(list, { state.eventSink(PostPageEvent.OpenProfile(it)) }, inset) }
            HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            CommentsPreviewBlock(page.comments, Modifier.padding(bottom = 8.dp))
        }
    }
}
