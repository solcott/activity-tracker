package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.activityfile.TimeZones
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.launchAction
import io.github.solcott.activitytracker.app.rememberShownName
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.report.ReportingEvent
import io.github.solcott.activitytracker.app.report.blockReported
import io.github.solcott.activitytracker.app.report.rememberReporting
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.segments.TrailReports
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.TrailReportView
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import io.github.solcott.uistate.pageOf
import io.github.solcott.uistate.updated
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.TimeZone

/**
 * Every report on a segment (segments/trail-reports), newest first and paged: the last 14 days, then older ones. The viewer deletes their
 * own; anyone else's they report or block. Their deletes and removed photos show at once over the pages already read.
 */
@CircuitInject(TrailReportsScreen::class, SessionScope::class)
@Composable
@Suppress("LongParameterList") // The reads and writes of the list, and the segment's name for a page opened by link.
fun TrailReportsPresenter(
    screen: TrailReportsScreen,
    navigator: Navigator,
    reports: TrailReports,
    segments: SegmentRepository,
    moderation: ReportRepository,
    blocks: BlockRepository,
    clock: Clock,
): TrailReportsState {
    val segment = AtUri(screen.segment)
    val zone = remember { TimeZones.device() }
    val self = reports.self.value
    val flights = rememberSingleFlight()
    var error by rememberRetained { mutableStateOf<UiError?>(null) }
    val onError: (UiError) -> Unit = { error = it }
    val page =
        rememberRetainedPaging(screen, idOf = TrailReportRow::uri) { cursor ->
            flow {
                emit(
                    reports.reportsPaged(segment, cursor).mapData { read ->
                        val now = clock.now()
                        pageOf(read.items.map { rowOf(it, now, zone, self) }, read.next)
                    },
                )
            }
        }
    // The viewer's deletes and removed photos show at once.
    val changes by reports.changes.collectAsState()
    val shown =
        remember(page.state, changes) {
            val change = changes[segment.value] ?: return@remember page.state
            page.state.updated(TrailReportRow::uri) { rows ->
                rows
                    .filterNot { it.uri in change.deleted }
                    .map { row -> row.copy(photos = row.photos.filterNot { it.url in change.removedPhotos }.toImmutableList()) }
            }
        }

    val reporting = rememberReporting(moderation) { author, _ -> flights.blockReported(author, blocks) { error = it } }

    /** The row at [uri] shown in the list, if [own] says whose it is. */
    fun find(uri: String, own: Boolean) = shown.items.firstOrNull { it.uri == uri }?.takeIf { it.own == own }

    return TrailReportsState(
        name = rememberShownName(screen.name, segment) { segments.segment(segment).currentName },
        reports = shown,
        reporting = reporting,
        error = error,
    ) { event ->
        when (event) {
            TrailReportsEvent.Back -> navigator.pop()
            TrailReportsEvent.Retry -> page.retry()
            TrailReportsEvent.LoadMore -> page.loadMore()
            is TrailReportsEvent.Delete ->
                find(event.uri, own = true)?.let { row ->
                    flights.launchAction(row.uri, UiError.Kind.DeleteTrailReport, onError) { reports.delete(AtUri(row.uri), segment) }
                }
            is TrailReportsEvent.Report ->
                find(event.uri, own = false)?.let { row ->
                    reporting.eventSink(
                        ReportingEvent.Open(ReportTarget.TrailReport(AtUri(row.uri), Cid(row.cid), Did(row.authorDid)), row.author),
                    )
                }
            is TrailReportsEvent.Block ->
                find(event.uri, own = false)?.let { row ->
                    flights.launchAction("block", UiError.Kind.UpdateBlock, onError) { blocks.setBlocked(Did(row.authorDid), true) }
                }
            is TrailReportsEvent.OpenPhoto -> {
                val before = shown.items.takeWhile { it.uri != event.uri }.sumOf { it.photos.size }
                navigator.goTo(TrailPhotosScreen(segment = screen.segment, start = before + event.position - 1))
            }
            TrailReportsEvent.DismissError -> error = null
        }
    }
}

/** [report] as a row at [now] in [zone], marked when it's [self]'s own. */
private fun rowOf(report: TrailReportView, now: Instant, zone: TimeZone, self: String) =
    TrailReportRow(
        uri = report.uri.value,
        cid = report.cid.value,
        author = report.author.shownName,
        authorDid = report.author.did.value,
        avatar = report.author.avatar,
        age = TrailReportTexts.age(report.indexedAt, now, zone),
        tags = report.conditions.map(TrailReportTexts::tag).toImmutableList(),
        note = report.note,
        photos = report.images.map { PhotoThumb(it.fullsize, it.alt, TrailReportTexts.blurOf(report)) }.toImmutableList(),
        current = TrailReportTexts.isCurrent(report, now),
        own = report.author.did.value == self,
    )
