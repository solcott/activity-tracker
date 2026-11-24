package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.activityfile.TimeZones
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.launchAction
import io.github.solcott.activitytracker.app.mapContent
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.report.ReportingEvent
import io.github.solcott.activitytracker.app.report.rememberReporting
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.data.segments.TrailReports
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.TrailPhotoView
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.LoadStatus
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import kotlin.time.Clock
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flow

/**
 * Trail photos full screen (segments/trail-reports): one segment's, or a trail's, paged as the viewer swipes. The viewer removes their own
 * from the trail; anyone else's they report or block.
 */
@CircuitInject(TrailPhotosScreen::class, SessionScope::class)
@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod") // The reads and writes of the viewer, an event per action.
fun TrailPhotosPresenter(
    screen: TrailPhotosScreen,
    navigator: Navigator,
    reports: TrailReports,
    moderation: ReportRepository,
    blocks: BlockRepository,
    clock: Clock,
): TrailPhotosState {
    val segment = screen.segment?.let(::AtUri)
    val path = screen.path?.let(::AtUri)
    val zone = remember { TimeZones.device() }
    val flights = rememberSingleFlight()
    var index by rememberRetained { mutableIntStateOf(screen.start.coerceAtLeast(0)) }
    var error by rememberRetained { mutableStateOf<UiError?>(null) }
    // The failed page load the viewer dismissed, which then stops showing until another fails.
    var dismissedPageFailure by rememberRetained { mutableStateOf<LoadStatus?>(null) }
    val page =
        rememberRetainedPaging(screen, idOf = { photo: TrailPhotoView -> "${photo.report.value}#${photo.position}" }) { cursor ->
            flow { emit(reports.photosPaged(segment, path, cursor)) }
        }
    val pageFailure = (page.state.append as? LoadStatus.Failed)?.takeIf { it != dismissedPageFailure }
    val changes by reports.changes.collectAsState()
    val photos = remember(page.state, changes) { page.state.origin?.let { reports.applyTo(page.state.items) } }
    val first = ContentState(photos, page.state.origin, page.state.refresh)
    val self = reports.self.value

    fun shownPhoto(): TrailPhotoView? = photos?.getOrNull(index)

    /** The next page, or the one that failed again. */
    fun loadMore() = if (page.state.append is LoadStatus.Failed) page.retry() else page.loadMore()

    val reporting =
        rememberReporting(moderation) { author, _ ->
            flights.launchAction("block", UiError.Kind.UpdateBlock, { error = it }) { blocks.setBlocked(author, true) }
        }

    val now = clock.now()
    return TrailPhotosState(
        content =
            first.mapContent {
                photos
                    ?.map { photo ->
                        TrailPhotoPage(
                            url = photo.image.fullsize,
                            alt = photo.image.alt,
                            blur = TrailReportTexts.thumb(photo).blur,
                            author = TrailReportTexts.nameOf(photo.author),
                            age = TrailReportTexts.age(photo.indexedAt, now, zone),
                            tags = TrailReportTexts.tags(photo.conditions),
                            report = photo.report.value,
                            position = photo.position,
                            segment = photo.segment.value,
                            own = photo.author.did.value == self,
                        )
                    }
                    ?.toImmutableList()
            },
        index = index.coerceIn(0, ((photos?.size ?: 1) - 1).coerceAtLeast(0)),
        total = photos?.size?.takeIf { page.state.endReached },
        reporting = reporting,
        // A page that failed to load shows like a failed action, over the photos already read; the next swipe tries it again.
        error = error ?: pageFailure?.let { UiError(UiError.Kind.LoadTrailReports, it.error) },
    ) { event ->
        when (event) {
            TrailPhotosEvent.Back -> navigator.pop()
            TrailPhotosEvent.Retry -> page.retry()
            is TrailPhotosEvent.Show -> {
                index = event.index.coerceAtLeast(0)
                if (event.index >= (photos?.size ?: 0) - PREFETCH) loadMore()
            }
            TrailPhotosEvent.OpenReport -> {
                val photo = shownPhoto() ?: return@TrailPhotosState
                navigator.goTo(TrailReportsScreen(photo.segment.value))
            }
            TrailPhotosEvent.OpenSegment -> shownPhoto()?.let { navigator.goTo(SegmentScreen(it.segment.value)) }
            TrailPhotosEvent.Report -> {
                val photo = shownPhoto()?.takeIf { it.author.did.value != self } ?: return@TrailPhotosState
                val report = ReportTarget.TrailReport(photo.report, photo.cid, photo.author.did)
                reporting.eventSink(
                    ReportingEvent.Open(ReportTarget.TrailPhoto(report, photo.position), TrailReportTexts.nameOf(photo.author)),
                )
            }
            TrailPhotosEvent.Block -> {
                val photo = shownPhoto()?.takeIf { it.author.did.value != self } ?: return@TrailPhotosState
                flights.launchAction("block", UiError.Kind.UpdateBlock, { error = it }) { blocks.setBlocked(photo.author.did, true) }
            }
            TrailPhotosEvent.Remove -> {
                val photo = shownPhoto()?.takeIf { it.author.did.value == self } ?: return@TrailPhotosState
                flights.launchAction("remove", UiError.Kind.DeleteTrailReport, { error = it }) { reports.removePhoto(photo) }
            }
            TrailPhotosEvent.DismissError -> {
                if (error == null) dismissedPageFailure = pageFailure
                error = null
            }
        }
    }
}

/** How close to the last photo read a swipe loads the next page. */
private const val PREFETCH = 3
