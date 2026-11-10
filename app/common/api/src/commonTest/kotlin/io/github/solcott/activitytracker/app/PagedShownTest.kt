package io.github.solcott.activitytracker.app

import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.LoadStatus
import io.github.solcott.uistate.Page
import io.github.solcott.uistate.PagedState
import io.github.solcott.uistate.applyFirstPage
import io.github.solcott.uistate.applyNextPage
import io.github.solcott.uistate.pagedStateOf
import io.github.solcott.uistate.refreshing
import kotlin.test.Test
import kotlin.test.assertEquals

/** ui/content-loading: what a paged list shows around its items, in each of its states. */
class PagedShownTest {
    private val kind = UiError.Kind.LoadBlocks
    private val offline = PagedShown.Status.Failure(UiError(kind, DataError.Network))
    private val failed = Outcome.Error(DataError.Network, Origin.Network)

    private fun loaded(items: List<String>, next: String? = null): PagedState<String, Unit> =
        pagedStateOf(Outcome.Data(Page(items, Unit, next), Origin.Network)) { it }

    private fun shown(
        above: PagedShown.Status? = null,
        empty: Boolean = false,
        below: PagedShown.Status? = null,
        first: Boolean = false,
        end: Boolean = false,
    ) = PagedShown(above, empty, below, first, end)

    @Test
    fun beforeTheFirstAnswerItShowsProgressAlone() =
        assertEquals(shown(below = PagedShown.Status.Progress, first = true), PagedShown.of(pagedStateOf<String, Unit>(), kind))

    @Test
    fun aFailedFirstPageSaysWhyInPlaceOfTheList() =
        assertEquals(shown(below = offline, first = true), PagedShown.of(pagedStateOf<String, Unit>().applyFirstPage(failed) { it }, kind))

    @Test fun aLoadedPageWithMoreToComeShowsNothingAround() = assertEquals(shown(), PagedShown.of(loaded(listOf("bob"), next = "2"), kind))

    @Test fun theLastPageMarksTheEnd() = assertEquals(shown(end = true), PagedShown.of(loaded(listOf("bob")), kind))

    @Test fun anEmptyAnswerShowsTheEmptyMessage() = assertEquals(shown(empty = true), PagedShown.of(loaded(emptyList()), kind))

    @Test
    fun aRefreshShowsProgressAboveAndNotTheEmptyMessage() {
        assertEquals(shown(above = PagedShown.Status.Progress, end = true), PagedShown.of(loaded(listOf("bob")).refreshing(), kind))
        assertEquals(shown(above = PagedShown.Status.Progress), PagedShown.of(loaded(emptyList()).refreshing(), kind))
    }

    @Test
    fun aFailedRefreshSaysWhyAboveTheItems() =
        assertEquals(
            shown(above = offline, end = true),
            PagedShown.of(loaded(listOf("bob")).refreshing().applyFirstPage(failed) { it }, kind),
        )

    @Test
    fun theNextPageShowsBelowTheItems() {
        val more = loaded(listOf("bob"), next = "2")
        assertEquals(shown(below = PagedShown.Status.Progress), PagedShown.of(more.applyNextPage("2", Outcome.Loading) { it }, kind))
        assertEquals(shown(below = offline), PagedShown.of(more.applyNextPage("2", failed) { it }, kind))
    }

    @Test
    fun aListTakenApartReadsTheSame() =
        assertEquals(
            PagedShown.of(loaded(listOf("bob")), kind),
            PagedShown.of(
                hasAnswer = true,
                isEmpty = false,
                refresh = LoadStatus.Idle,
                append = LoadStatus.Idle,
                endReached = true,
                errorKind = kind,
            ),
        )
}
