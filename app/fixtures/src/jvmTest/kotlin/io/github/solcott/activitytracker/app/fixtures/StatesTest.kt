package io.github.solcott.activitytracker.app.fixtures

import io.github.solcott.uistate.LoadStatus
import io.github.solcott.uistate.hasAnswer
import io.github.solcott.uistate.hasLoaded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** Each builder is in the ui/content-loading state it names. */
class StatesTest {
    @Test
    fun contentStates() {
        with(ContentStates.loading(0)) {
            assertFalse(hasLoaded)
            assertEquals(LoadStatus.Loading, status)
        }
        with(ContentStates.failed(0)) {
            assertFalse(hasLoaded)
            assertIs<LoadStatus.Failed>(status)
        }
        with(ContentStates.loaded(1)) {
            assertTrue(hasLoaded)
            assertEquals(LoadStatus.Idle, status)
        }
        with(ContentStates.refreshing(1)) {
            assertTrue(hasLoaded)
            assertEquals(LoadStatus.Loading, status)
        }
        with(ContentStates.refreshFailed(1)) {
            assertTrue(hasLoaded)
            assertIs<LoadStatus.Failed>(status)
        }
    }

    @Test
    fun pagedStates() {
        val items = persistentListOf("a", "b")
        with(PagedStates.loading<String, Unit>()) {
            assertFalse(hasAnswer)
            assertEquals(LoadStatus.Loading, refresh)
        }
        with(PagedStates.failed<String, Unit>()) {
            assertFalse(hasAnswer)
            assertIs<LoadStatus.Failed>(refresh)
        }
        with(PagedStates.loaded(items, PagedStates.NEXT)) {
            assertTrue(hasAnswer)
            assertEquals(items, this.items)
            assertTrue(canLoadMore)
        }
        with(PagedStates.refreshing(items, Unit)) {
            assertTrue(hasAnswer)
            assertEquals(LoadStatus.Loading, refresh)
            assertEquals(items, this.items)
        }
        with(PagedStates.refreshFailed(items, Unit)) {
            assertTrue(hasAnswer)
            assertIs<LoadStatus.Failed>(refresh)
            assertEquals(items, this.items)
        }
        with(PagedStates.loadingMore(items, Unit)) {
            assertEquals(LoadStatus.Loading, append)
            assertEquals(items, this.items)
        }
        with(PagedStates.pageFailed(items, Unit)) {
            assertIs<LoadStatus.Failed>(append)
            assertEquals(items, this.items)
        }
        with(PagedStates.end(items, Unit)) {
            assertTrue(endReached)
            assertFalse(canLoadMore)
        }
        assertTrue(PagedStates.end(emptyList<String>(), Unit).items.isEmpty())
    }
}
