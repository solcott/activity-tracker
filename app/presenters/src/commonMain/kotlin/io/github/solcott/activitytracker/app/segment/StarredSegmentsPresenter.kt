package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.segments.SegmentStar
import io.github.solcott.activitytracker.data.segments.SegmentStars
import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import io.github.solcott.dataresult.dataOrNull
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.Page
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import io.github.solcott.uistate.pageOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@CircuitInject(StarredSegmentsScreen::class, SessionScope::class)
@Composable
fun StarredSegmentsPresenter(navigator: Navigator, stars: SegmentStars, segments: SegmentRepository): StarredSegmentsState {
    val pages = rememberRetained { StarPages(stars, segments) }
    val list =
        rememberRetainedPaging(StarredSegmentsScreen, idOf = StarredSegment::uri, enabled = stars.canUse) { cursor ->
            flow { emit(pages.load(cursor)) }
        }
    var actionError by rememberRetained { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()

    return StarredSegmentsState(
        segments = list.state,
        removing = list.state.items.map { it.uri }.filter { flights.isRunning(it) }.toImmutableSet(),
        actionError = actionError,
        needsSignIn = !stars.canUse,
    ) { event ->
        when (event) {
            StarredSegmentsEvent.Back -> {
                navigator.pop()
            }

            StarredSegmentsEvent.LoadMore -> {
                list.loadMore()
            }

            StarredSegmentsEvent.Retry -> {
                list.retry()
            }

            is StarredSegmentsEvent.Open -> {
                list.state.items.firstOrNull { it.uri == event.uri }?.segment?.let { navigator.goTo(SegmentScreen(it.uri.value)) }
            }

            is StarredSegmentsEvent.Unstar -> {
                pages.starred
                    .firstOrNull { it.segment.uri == event.uri }
                    ?.let { star ->
                        actionError = null
                        flights.launch(event.uri) {
                            runSuspendCatching { stars.setStarred(star.segment, starred = false) }
                                .onSuccess { list.update { items -> items.filterNot { it.uri == event.uri } } }
                                .onFailure { actionError = uiErrorOf(UiError.Kind.UpdateStar, it) }
                        }
                    }
            }
        }
    }
}

/**
 * The stars resolved a page at a time. Every star is read for the first page (the PDS lists them by a hash, so all are needed to order
 * them), and each page's segments are then looked up. Either failing fails the page, with why.
 */
private class StarPages(private val stars: SegmentStars, private val segments: SegmentRepository) {
    var starred: List<SegmentStar> = emptyList()
        private set

    suspend fun load(cursor: String?): Outcome<Page<StarredSegment, Unit>> =
        when (val read = if (cursor == null) stars.starredOutcome() else Outcome.Data(starred, Origin.Network)) {
            is Outcome.Data -> {
                starred = read.data
                pageAt(cursor?.toIntOrNull() ?: 0)
            }
            is Outcome.Error -> read
            Outcome.Loading -> Outcome.Loading
        }

    private suspend fun pageAt(from: Int): Outcome<Page<StarredSegment, Unit>> {
        val page = starred.drop(from).take(PAGE_SIZE)
        val next = (from + page.size).takeIf { it < starred.size }?.toString()
        return segments.resolve(page).mapData { pageOf(it, next) }
    }
}

/**
 * Each of [stars]' segments from the AppView, a few at a time: one it no longer serves is listed as unavailable, and any other failed
 * lookup fails them all.
 */
private suspend fun SegmentRepository.resolve(stars: List<SegmentStar>): Outcome<List<StarredSegment>> = coroutineScope {
    val permits = Semaphore(CONCURRENT_LOOKUPS)
    val lookups = stars.map { star -> async { permits.withPermit { star.segment.uri to segmentLookup(star.segment.uri) } } }.awaitAll()
    lookups.firstNotNullOfOrNull { (_, lookup) -> lookup as? Outcome.Error }
        ?: Outcome.Data(lookups.map { (uri, lookup) -> StarredSegment(uri, lookup.dataOrNull()) }, Origin.Network)
}

private const val PAGE_SIZE = 30
private const val CONCURRENT_LOOKUPS = 4
