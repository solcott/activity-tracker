package io.github.solcott.activitytracker.app.comments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.detail.CARD_PADDING
import io.github.solcott.activitytracker.app.interactions.CommentComposer
import io.github.solcott.activitytracker.app.interactions.CommentsTexts
import io.github.solcott.activitytracker.app.interactions.LoadMoreNearTheEnd
import io.github.solcott.activitytracker.app.interactions.comments
import io.github.solcott.activitytracker.app.report.ReportingHost
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.uistate.hasLoaded

/**
 * A subject's comments (feed/comments): the count, the threads a page at a time as the viewer scrolls, with their replies and actions, and
 * the field at the screen's foot, riding above the keyboard. Opened from "Add a comment" ([CommentsScreenState.focus]) the field takes
 * focus once; choosing Reply on a comment takes it again.
 */
@CircuitInject(CommentsScreen::class, SessionScope::class)
@Composable
fun CommentsUi(state: CommentsScreenState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val page = state.content.data.takeIf { state.content.hasLoaded }
    page?.let { LoadMoreNearTheEnd(listState, it.comments) }
    val retry = { state.eventSink(CommentsScreenEvent.Retry) }
    ListScreen(
        title = CommentsTexts.title.string(),
        subtitle = page?.subtitle?.string(),
        onBack = { state.eventSink(CommentsScreenEvent.Back) },
        content = state.content,
        errorKind = state.loadError,
        onRetry = retry,
        modifier = modifier,
        listState = listState,
        verticalPadding = 12.dp,
        bottomBar = { page?.let { Composer(it, state.focus) } },
    ) { loaded ->
        item(key = "refresh") { RefreshStatus(state.content, state.loadError, retry) }
        comments(loaded.comments, state.reporting)
    }
    ReportingHost(state.reporting)
}

/**
 * The field at the foot, over a hairline, kept above the keyboard and the navigation bar. It takes focus once as the screen first opens
 * when [focusOnOpen] (not again on coming back), and whenever the viewer chooses a comment to reply to.
 */
@Composable
private fun Composer(page: CommentsPage, focusOnOpen: Boolean) {
    val focusRequester = remember { FocusRequester() }
    var focused by rememberSaveable { mutableStateOf(false) }
    SideEffect(Unit) {
        if (focusOnOpen && !focused) {
            focused = true
            focusRequester.requestFocus()
        }
    }
    val replyingTo = page.comments.replyingTo?.uri
    SideEffect(replyingTo) { if (replyingTo != null) focusRequester.requestFocus() }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CommentComposer(
                page.comments,
                Modifier.readableWidth().padding(horizontal = CARD_PADDING, vertical = 8.dp),
                focusRequester,
            )
        }
    }
}
