package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.PagedStates
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.pagedFooter

@PreviewTest
@PreviewComponentVariants
@Composable
private fun FirstLoad() {
    ComponentPreview {
        ContentStateLayout(ContentStates.loading(null), UiError.Kind.LoadFeed, onRetry = {}, Modifier.heightIn(max = 120.dp)) {}
        ContentStateLayout(ContentStates.failed(null), UiError.Kind.LoadFeed, onRetry = {}, Modifier.heightIn(max = 160.dp)) {}
    }
}

@PreviewTest
@PreviewComponentVariants
@Composable
private fun Refresh() {
    ComponentPreview {
        RefreshStatus(ContentStates.refreshing(Unit), UiError.Kind.LoadFeed, onRetry = {})
        RefreshStatus(ContentStates.refreshFailed(Unit), UiError.Kind.LoadFeed, onRetry = {})
    }
}

@PreviewTest
@PreviewComponentVariants
@Composable
private fun PagedFooters() {
    val items = listOf("Morning Ride")
    ComponentPreview {
        for (state in listOf(PagedStates.loadingMore(items, Unit), PagedStates.pageFailed(items, Unit), PagedStates.end(items, Unit))) {
            LazyColumn(Modifier.heightIn(max = 160.dp)) {
                pagedFooter(state, UiError.Kind.LoadFeed, onRetry = {}) { Text("No activities") }
            }
        }
    }
}
