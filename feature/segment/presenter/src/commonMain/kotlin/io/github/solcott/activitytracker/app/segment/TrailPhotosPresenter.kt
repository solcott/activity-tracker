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
import io.github.solcott.activitytracker.app.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.data.segments.TrailReports
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.GetTrailPhotos
import io.github.solcott.activitytracker.lexicon.app.TrailPhotoView
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlin.time.Clock
import kotlinx.collections.immutable.toImmutableList

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
    var more by rememberRetained { mutableStateOf<List<TrailPhotoView>>(emptyList()) }
    var moreCursor by rememberRetained { mutableStateOf<String?>(null) }
    var index by rememberRetained { mutableIntStateOf(screen.start.coerceAtLeast(0)) }
    var loadKey by rememberRetained { mutableIntStateOf(0) }
    var error by rememberRetained { mutableStateOf<UiError?>(null) }
    val first =
        produceRetainedContentState<GetTrailPhotos.Output?>(null, loadKey) {
            more = emptyList()
            moreCursor = null
            outcomeFlow { reports.photosPage(segment, path) }
        }
    val changes by reports.changes.collectAsState()
    val cursor = if (more.isEmpty()) first.data?.cursor else moreCursor
    val photos = remember(first.data, more, changes) { first.data?.let { reports.applyTo(it.photos + more) } }
    val self = reports.self.value

    fun shownPhoto(): TrailPhotoView? = photos?.getOrNull(index)

    fun loadMore() {
        val next = cursor ?: return
        flights.launch("more") {
            runSuspendCatching { reports.photosPage(segment, path, next) }
                .onSuccess {
                    more = more + it.photos
                    moreCursor = it.cursor
                }
                .onFailure { error = uiErrorOf(UiError.Kind.LoadTrailReports, it) }
        }
    }

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
        total = photos?.size?.takeIf { cursor == null },
        reporting = reporting,
        error = error,
    ) { event ->
        when (event) {
            TrailPhotosEvent.Back -> navigator.pop()
            TrailPhotosEvent.Retry -> loadKey++
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
            TrailPhotosEvent.DismissError -> error = null
        }
    }
}

/** How close to the last photo read a swipe loads the next page. */
private const val PREFETCH = 3
