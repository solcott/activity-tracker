package io.github.solcott.activitytracker.app

import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.Origin
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.LoadStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/** ui/content-loading: what a screen shows for content it loads, in each of its states. */
class ContentShownTest {
    private val kind = UiError.Kind.LoadFeed
    private val offline = UiError(kind, DataError.Network)

    @Test fun beforeTheFirstAnswerItShowsProgress() = assertEquals(ContentShown.Progress, ContentShown.of(ContentState(""), kind))

    @Test
    fun aFirstLoadThatFailsSaysWhatAndWhy() =
        assertEquals(ContentShown.Failure(offline), ContentShown.of(ContentState("", null, LoadStatus.Failed(DataError.Network)), kind))

    @Test
    fun loadedContentShowsWithNothingOverIt() =
        assertEquals(
            ContentShown.Content(refreshError = null, refreshing = false),
            ContentShown.of(ContentState("feed", Origin.Network, LoadStatus.Idle), kind),
        )

    @Test
    fun contentStaysWhileItRefreshes() =
        assertEquals(
            ContentShown.Content(refreshError = null, refreshing = true),
            ContentShown.of(ContentState("feed", Origin.Network, LoadStatus.Loading), kind),
        )

    @Test
    fun aFailedRefreshKeepsTheContentAndSaysWhy() =
        assertEquals(
            ContentShown.Content(refreshError = offline, refreshing = false),
            ContentShown.of(ContentState("feed", Origin.Network, LoadStatus.Failed(DataError.Network)), kind),
        )
}
