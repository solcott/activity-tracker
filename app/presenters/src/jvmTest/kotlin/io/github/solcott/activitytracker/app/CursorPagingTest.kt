package io.github.solcott.activitytracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cash.turbine.ReceiveTurbine
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.test.presenterTestOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

class CursorPagingTest {
    private var key by mutableStateOf("x")
    private var enabled by mutableStateOf(true)

    /** Each load, as `key/cursor`, in order. */
    private val calls = mutableListOf<String>()
    private val gates = mutableMapOf<String, CompletableDeferred<CursorPage<String>>>()

    private fun gate(call: String) = gates.getOrPut(call) { CompletableDeferred() }

    private fun answer(call: String, vararg items: String, cursor: String? = null) = gate(call).complete(CursorPage(items.toList(), cursor))

    private fun fail(call: String) = gate(call).completeExceptionally(IllegalStateException("down"))

    private class Shown(
        val items: List<String>,
        val isLoading: Boolean,
        val canLoadMore: Boolean,
        val loaded: Boolean,
        val failure: Throwable?,
        val paging: CursorPaging<String>,
    ) : CircuitUiState

    private fun test(body: suspend ReceiveTurbine<Shown>.() -> Unit) = runTest {
        presenterTestOf({
            val current = key
            val paging =
                rememberCursorPaging(current, idOf = { it }, enabled = enabled) { cursor ->
                    val call = "$current/$cursor"
                    calls += call
                    gate(call).await()
                }
            Shown(paging.items, paging.isLoading, paging.canLoadMore, paging.loaded, paging.failure, paging)
        }) {
            body(this)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aPageAppendsDeDuplicatedById() = test {
        answer("x/null", "a", "b", cursor = "1")
        val first = awaitUntil { it.loaded && !it.isLoading }
        assertEquals(listOf("a", "b"), first.items)
        assertTrue(first.canLoadMore)

        answer("x/1", "b", "c")
        first.paging.loadMore()
        val second = awaitUntil { it.items.size == 3 && !it.isLoading }
        assertEquals(listOf("a", "b", "c"), second.items, "the repeat keeps its first place")
        assertFalse(second.canLoadMore, "no cursor, no more")

        second.paging.loadMore()
        assertEquals(listOf("x/null", "x/1"), calls, "loadMore at the end loads nothing")
    }

    @Test
    fun loadMoreIsIgnoredWhileLoadingAndAfterAFailure() = test {
        val loading = awaitUntil { it.isLoading }
        loading.paging.loadMore()
        answer("x/null", "a", cursor = "1")
        val first = awaitUntil { it.loaded && !it.isLoading }

        first.paging.loadMore()
        first.paging.loadMore()
        awaitUntil { it.isLoading }
        fail("x/1")
        val failed = awaitUntil { it.failure != null && !it.isLoading }
        assertEquals(listOf("a"), failed.items, "a failed later page keeps what loaded")
        assertFalse(failed.canLoadMore)

        failed.paging.loadMore()
        assertEquals(listOf("x/null", "x/1"), calls, "one load each: none while loading, none after the failure")
    }

    @Test
    fun aKeyChangeCancelsAPageInFlight() = test {
        answer("x/null", "a", cursor = "1")
        awaitUntil { it.loaded && !it.isLoading }.paging.loadMore()
        awaitUntil { it.isLoading }
        assertEquals("x/1", calls.last())

        key = "y"
        val changing = awaitUntil { !it.loaded }
        assertEquals(listOf("a"), changing.items, "the shown items stay while the new key loads")
        answer("x/1", "stale")
        answer("y/null", "b")
        val changed = awaitUntil { it.loaded && !it.isLoading }
        assertEquals(listOf("b"), changed.items)
        assertEquals(listOf("x/null", "x/1", "y/null"), calls)
    }

    @Test
    fun aFailedFirstPageEmptiesTheList() = test {
        answer("x/null", "a")
        awaitUntil { it.loaded && !it.isLoading }

        key = "y"
        fail("y/null")
        val failed = awaitUntil { it.loaded && !it.isLoading }
        assertEquals(emptyList(), failed.items, "the previous key's items don't show under its error")
        assertNotNull(failed.failure)
    }

    @Test
    fun retryReloadsFromTheFirstPage() = test {
        answer("x/null", "a", cursor = "1")
        awaitUntil { it.loaded && !it.isLoading }.paging.loadMore()
        fail("x/1")
        val failed = awaitUntil { it.failure != null }

        gates.remove("x/null")
        answer("x/null", "a", "b")
        failed.paging.retry()
        val retried = awaitUntil { it.failure == null && !it.isLoading && it.items.size == 2 }
        assertEquals(listOf("a", "b"), retried.items)
        assertEquals(listOf("x/null", "x/1", "x/null"), calls)
    }

    @Test
    fun disablingEmptiesTheList() = test {
        answer("x/null", "a", cursor = "1")
        awaitUntil { it.loaded && !it.isLoading }

        enabled = false
        val disabled = awaitUntil { it.items.isEmpty() }
        assertFalse(disabled.loaded)
        assertFalse(disabled.isLoading)
        assertFalse(disabled.canLoadMore)
        assertNull(disabled.failure)
    }

    @Test
    fun updateEditsTheItems() = test {
        answer("x/null", "a", "b")
        awaitUntil { it.loaded && !it.isLoading }.paging.update { items -> items.filterNot { it == "a" } }
        assertEquals(listOf("b"), awaitUntil { it.items.size == 1 }.items)
    }
}
