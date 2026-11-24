package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import app.cash.turbine.ReceiveTurbine
import com.slack.circuit.test.CircuitReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailPage
import io.github.solcott.activitytracker.app.detail.ActivityDetailPresenter
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.app.interactions.LikesList

/** The loaded page; null until the activity first loads. */
internal fun ActivityDetailState.page(): ActivityDetailPage? = content.data

/** The detail screen's fakes, shared by its presenter tests: [ActivityTestBase]'s served activity, and the page's presenter over it. */
open class ActivityDetailTestBase : ActivityTestBase() {
    protected val screen = ActivityDetailScreen(activityUri)

    protected fun ActivityDetailState.loaded() = page()

    protected suspend fun detail(r: Repos, block: suspend CircuitReceiveTurbine<ActivityDetailState>.() -> Unit) =
        presenterTestOf(presenterOf(r), block = block)

    /** The page's presenter for [on], on [r]'s repositories. */
    protected fun presenterOf(
        r: Repos,
        on: ActivityDetailScreen = screen,
        navigator: FakeNavigator = FakeNavigator(on),
    ): @Composable () -> ActivityDetailState = {
        ActivityDetailPresenter(
            on,
            navigator,
            r.feed,
            r.activities,
            r.profiles,
            localLikes,
            r.manager,
            r.exporter,
            r.units,
            r.suggestions,
            r.blocks,
            r.reports,
            r.segments,
            r.diveSites,
            r.groups,
        )
    }

    protected suspend fun ReceiveTurbine<ActivityDetailState>.awaitLoaded() = awaitUntil { it.page() != null }.page()!!

    protected suspend fun ReceiveTurbine<ActivityDetailState>.awaitLikes(done: (LikesList) -> Boolean): ActivityDetailPage {
        var state = awaitLoaded()
        while (state.likesList?.let { !it.isLoading && done(it) } != true) state = awaitLoaded()
        return state
    }
}
