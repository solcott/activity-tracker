package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** A paged list asks for its next page only once its last row is on screen (ui/content-loading). */
@OptIn(ExperimentalTestApi::class)
class LoadMoreAtEndTest {
    @Test
    fun aFirstPageThatFillsTheScreenDoesNotFetchTheNext() = runComposeUiTest {
        var rows by mutableStateOf(emptyList<Int>())
        var canLoadMore by mutableStateOf(false)
        var asked = 0
        setContent {
            val listState = rememberLazyListState()
            LoadMoreAtEnd(listState, canLoadMore) { asked++ }
            LazyColumn(Modifier.size(300.dp, 400.dp), state = listState) {
                item { Text("Filters", Modifier.height(48.dp)) }
                // While the first page loads, the loading row is the list's last row, and it is on screen.
                if (rows.isEmpty()) item { Text("Loading", Modifier.height(48.dp)) }
                items(rows) { Text("Row $it", Modifier.height(48.dp)) }
            }
        }
        waitForIdle()

        // The first page arrives with a cursor: thirty rows, far more than fit.
        rows = (1..30).toList()
        canLoadMore = true
        waitForIdle()
        assertEquals(0, asked, "asked for the next page before reaching the end")

        onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(30)
        waitForIdle()
        assertEquals(1, asked)
    }

    @Test
    fun aFirstPageShorterThanTheScreenFetchesTheNext() = runComposeUiTest {
        var rows by mutableStateOf(emptyList<Int>())
        var canLoadMore by mutableStateOf(false)
        var asked = 0
        setContent {
            val listState = rememberLazyListState()
            LoadMoreAtEnd(listState, canLoadMore) { asked++ }
            LazyColumn(Modifier.size(300.dp, 400.dp), state = listState) {
                if (rows.isEmpty()) item { Text("Loading", Modifier.height(48.dp)) }
                items(rows) { Text("Row $it", Modifier.height(48.dp)) }
            }
        }
        waitForIdle()

        rows = (1..3).toList()
        canLoadMore = true
        waitForIdle()
        assertEquals(1, asked)
    }
}
