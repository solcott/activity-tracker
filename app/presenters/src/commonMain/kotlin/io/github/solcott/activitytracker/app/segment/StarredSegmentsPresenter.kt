package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.CursorPage
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberCursorPaging
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.segments.SegmentStar
import io.github.solcott.activitytracker.data.segments.SegmentStars
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@CircuitInject(StarredSegmentsScreen::class, SessionScope::class)
@Composable
fun StarredSegmentsPresenter(navigator: Navigator, stars: SegmentStars, segments: SegmentRepository): StarredSegmentsState {
    val pages = remember { StarPages(stars, segments) }
    val list =
        rememberCursorPaging(StarredSegmentsScreen, idOf = { row: StarredSegment -> row.uri }, enabled = stars.canUse, load = pages::load)
    var unstarError by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    val error =
        if (stars.canUse) unstarError ?: list.failure?.let { UiError.of(UiError.Kind.LoadStarredSegments, it) }
        else UiError(UiError.Kind.SignInAgain, null)

    return StarredSegmentsState(
        segments = list.items,
        isLoading = list.isLoading,
        canLoadMore = list.canLoadMore && unstarError == null,
        removing = list.items.map { it.uri }.filter { flights.isRunning(it) }.toImmutableSet(),
        error = error,
    ) { event ->
        when (event) {
            StarredSegmentsEvent.Back -> {
                navigator.pop()
            }

            StarredSegmentsEvent.LoadMore -> {
                if (unstarError == null) list.loadMore()
            }

            StarredSegmentsEvent.Retry -> {
                unstarError = null
                list.retry()
            }

            is StarredSegmentsEvent.Open -> {
                list.items.firstOrNull { it.uri == event.uri }?.segment?.let { navigator.goTo(SegmentScreen(it.uri.value)) }
            }

            is StarredSegmentsEvent.Unstar -> {
                pages.starred
                    .firstOrNull { it.segment.uri == event.uri }
                    ?.let { star ->
                        flights.launch(event.uri) {
                            runSuspendCatching { stars.setStarred(star.segment, starred = false) }
                                .onSuccess { list.update { items -> items.filterNot { it.uri == event.uri } } }
                                .onFailure { unstarError = uiErrorOf(UiError.Kind.UpdateStar, it) }
                        }
                    }
            }
        }
    }
}

/**
 * The stars resolved a page at a time. Every star is read for the first page (the PDS lists them by a hash, so all are needed to order
 * them), and each page's segments are then looked up.
 */
private class StarPages(private val stars: SegmentStars, private val segments: SegmentRepository) {
    var starred: List<SegmentStar> = emptyList()
        private set

    suspend fun load(cursor: String?): CursorPage<StarredSegment> {
        if (cursor == null) starred = stars.starred() ?: throw StarsUnreadable()
        val from = cursor?.toIntOrNull() ?: 0
        val page = starred.drop(from).take(PAGE_SIZE)
        val next = (from + page.size).takeIf { it < starred.size }?.toString()
        return CursorPage(segments.resolve(page), next)
    }
}

/** Each of [stars]' segments from the AppView, a few at a time; a segment it no longer serves (`NotFound`) is listed as unavailable. */
private suspend fun SegmentRepository.resolve(stars: List<SegmentStar>): List<StarredSegment> = coroutineScope {
    val permits = Semaphore(CONCURRENT_LOOKUPS)
    stars
        .map { star ->
            async {
                permits.withPermit {
                    val uri = star.segment.uri
                    val view = runSuspendCatching { segment(uri) }
                    StarredSegment(uri, view.getOrElse { e -> if (e.isNotFound()) null else throw e })
                }
            }
        }
        .awaitAll()
}

private fun Throwable.isNotFound() = this is XrpcException && error == "NotFound"

/** The athlete's `self` space couldn't be read, so the stars are unknown, not none. */
private class StarsUnreadable : Exception("Couldn't read your stars")

private const val PAGE_SIZE = 30
private const val CONCURRENT_LOOKUPS = 4
