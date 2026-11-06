package io.github.solcott.activitytracker.app.feed

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.settings.monthName
import io.github.solcott.activitytracker.app.ui.ActivityListCard
import io.github.solcott.activitytracker.app.ui.EmptyListText
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.pagedFooter
import io.github.solcott.activitytracker.app.ui.pagedRefreshStatus
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.import_batch_empty
import io.github.solcott.activitytracker.composeApp.resources.import_batch_month
import io.github.solcott.activitytracker.composeApp.resources.import_batch_title
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/** One batch import's activities (feed/import-cards), newest started first, under a heading for each month. */
@CircuitInject(ImportBatchScreen::class, SessionScope::class)
@Composable
fun ImportBatchUi(state: ImportBatchState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state.activities.canLoadMore) { state.eventSink(ImportBatchEvent.LoadMore) }
    val zone = LocalTimeZone.current
    val months =
        remember(state.activities.items, zone) {
            state.activities.items.groupBy {
                it.effectiveRecord().startedAt.toLocalDateTime(zone).date.let { d -> d.year to d.month.ordinal + 1 }
            }
        }
    ListScreen(
        title = stringResource(Res.string.import_batch_title, state.name),
        onBack = { state.eventSink(ImportBatchEvent.Back) },
        modifier = modifier,
        listState = listState,
    ) {
        state.error?.let { error -> item(key = "error") { ErrorText(error, Modifier.fillMaxWidth().padding(16.dp)) } }
        pagedRefreshStatus(state.activities, UiError.Kind.LoadImportBatch) { state.eventSink(ImportBatchEvent.Retry) }
        months.forEach { (month, activities) ->
            val (year, number) = month
            item(key = "month-$year-$number") {
                Text(
                    stringResource(Res.string.import_batch_month, monthName(number), year),
                    Modifier.padding(top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(activities, key = { it.uri.value }) { activity ->
                ActivityListCard(
                    activity,
                    state.viewerDid,
                    authorClickable = false,
                    state.revealed,
                    onEvent = { state.eventSink(ImportBatchEvent.Activity(it)) },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
        pagedFooter(state.activities, UiError.Kind.LoadImportBatch, onRetry = { state.eventSink(ImportBatchEvent.Retry) }) {
            EmptyListText(stringResource(Res.string.import_batch_empty))
        }
    }
}
