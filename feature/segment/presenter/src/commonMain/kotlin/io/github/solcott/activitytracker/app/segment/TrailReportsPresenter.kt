package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.launchAction
import io.github.solcott.activitytracker.app.mapContent
import io.github.solcott.activitytracker.app.rememberShownName
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.report.Reporting
import io.github.solcott.activitytracker.app.report.ReportingEvent
import io.github.solcott.activitytracker.app.report.rememberReporting
import io.github.solcott.activitytracker.app.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.segments.TrailReports
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.GetTrailReports
import io.github.solcott.activitytracker.lexicon.app.TrailReportView
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.TimeZone

/**
 * Every report on a segment (segments/trail-reports), newest first and paged: the last 14 days, then older ones. The viewer deletes their
 * own; anyone else's they report or block.
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
    val flights = rememberSingleFlight()
    val later = rememberRetained { LaterReports() }
    var loadKey by rememberRetained { mutableIntStateOf(0) }
    var error by rememberRetained { mutableStateOf<UiError?>(null) }
    val onError: (UiError) -> Unit = { error = it }
    // The first page; later pages add to [later]. A retry starts over.
    val first =
        produceRetainedContentState<GetTrailReports.Output?>(null, loadKey) {
            later.clear()
            outcomeFlow { reports.reportsPage(segment) }
        }
    // The viewer's deletes and removed photos show at once.
    val changes by reports.changes.collectAsState()
    val cursor = if (later.reports.isEmpty()) first.data?.cursor else later.cursor
    val shown =
        remember(first.data, later.reports, changes) {
            first.data?.let { page ->
                reports.applyTo(segment, page.reports + later.reports.filter { m -> page.reports.none { it.uri == m.uri } })
            }
        }
    val self = reports.self.value

    val reporting =
        rememberReporting(moderation) { author, _ ->
            flights.launchAction("block", UiError.Kind.UpdateBlock, onError) { blocks.setBlocked(author, true) }
        }
    val actions = ReportActions(flights, reports, blocks, reporting, onError)

    /** The report at [uri] shown in the list, if [own] says whose it is. */
    fun find(uri: String, own: Boolean) = shown?.firstOrNull { it.uri.value == uri }?.takeIf { (it.author.did.value == self) == own }

    return TrailReportsState(
        name = rememberShownName(screen.name, segment) { segments.segment(segment).currentName },
        content = first.mapContent { shown?.let { list -> rowsOf(list, clock.now(), zone, self) } },
        loadingMore = later.loading,
        hasMore = cursor != null,
        reporting = reporting,
        error = error,
    ) { event ->
        when (event) {
            TrailReportsEvent.Back -> navigator.pop()
            TrailReportsEvent.Retry -> loadKey++
            TrailReportsEvent.LoadMore -> later.load(flights, cursor, { reports.reportsPage(segment, it) }, onError)
            is TrailReportsEvent.Delete -> find(event.uri, own = true)?.let(actions::delete)
            is TrailReportsEvent.Report -> find(event.uri, own = false)?.let(actions::report)
            is TrailReportsEvent.Block -> find(event.uri, own = false)?.let(actions::block)
            is TrailReportsEvent.OpenPhoto -> {
                val before = shown.orEmpty().takeWhile { it.uri.value != event.uri }.sumOf { it.images.size }
                navigator.goTo(TrailPhotosScreen(segment = screen.segment, start = before + event.position - 1))
            }
            TrailReportsEvent.DismissError -> error = null
        }
    }
}

/** [list] as rows at [now] in [zone], marking [self]'s own and where each section starts. */
private fun rowsOf(list: List<TrailReportView>, now: Instant, zone: TimeZone, self: String): ImmutableList<TrailReportRow> =
    list
        .mapIndexed { index, report ->
            val current = TrailReportTexts.isCurrent(report, now)
            val previous = list.getOrNull(index - 1)?.let { TrailReportTexts.isCurrent(it, now) }
            TrailReportRow(
                uri = report.uri.value,
                author = TrailReportTexts.nameOf(report.author),
                authorDid = report.author.did.value,
                avatar = report.author.avatar,
                age = TrailReportTexts.age(report.indexedAt, now, zone),
                tags = report.conditions.map(TrailReportTexts::tag).toImmutableList(),
                note = report.note,
                photos = report.images.map { PhotoThumb(it.fullsize, it.alt, TrailReportTexts.blurOf(report)) }.toImmutableList(),
                current = current,
                own = report.author.did.value == self,
                startsSection = previous == null || previous != current,
            )
        }
        .toImmutableList()

/** The pages after the first, which the list adds to it, and whether one is loading. */
@Stable
private class LaterReports {
    var reports by mutableStateOf<List<TrailReportView>>(emptyList())
        private set

    var cursor by mutableStateOf<String?>(null)
        private set

    var loading by mutableStateOf(false)
        private set

    /** Forgets every later page, as a retry starts over. */
    fun clear() {
        reports = emptyList()
        cursor = null
    }

    /** Loads the page after [next] with [page], once at a time; a failure goes to [onError]. */
    fun load(flights: SingleFlight, next: String?, page: suspend (String) -> GetTrailReports.Output, onError: (UiError) -> Unit) {
        if (next == null || loading) return
        loading = true
        flights.launch(TrailReportsEvent.LoadMore) {
            runSuspendCatching { page(next) }
                .onSuccess {
                    reports = reports + it.reports
                    cursor = it.cursor
                }
                .onFailure { onError(uiErrorOf(UiError.Kind.LoadTrailReports, it)) }
            loading = false
        }
    }
}

/** What the viewer does to one report: delete their own, report or block someone else's. */
private class ReportActions(
    private val flights: SingleFlight,
    private val reports: TrailReports,
    private val blocks: BlockRepository,
    private val reporting: Reporting,
    private val onError: (UiError) -> Unit,
) {
    fun delete(report: TrailReportView) {
        flights.launchAction(report.uri.value, UiError.Kind.DeleteTrailReport, onError) { reports.delete(report) }
    }

    fun report(report: TrailReportView) =
        reporting.eventSink(
            ReportingEvent.Open(
                ReportTarget.TrailReport(report.uri, report.cid, report.author.did),
                TrailReportTexts.nameOf(report.author),
            ),
        )

    fun block(report: TrailReportView) {
        flights.launchAction("block", UiError.Kind.UpdateBlock, onError) { blocks.setBlocked(report.author.did, true) }
    }
}
