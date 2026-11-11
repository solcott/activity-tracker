package io.github.solcott.activitytracker.app.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.activityfile.TimeZones
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.activitylist.rememberActivityListEventSink
import io.github.solcott.activitytracker.app.feedfilter.FeedFilter
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterScreen
import io.github.solcott.activitytracker.app.moderation.rememberRevealed
import io.github.solcott.activitytracker.app.posts.rememberPostEventSink
import io.github.solcott.activitytracker.app.posts.rememberQuoting
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.app.record.ManualActivityScreen
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.report.rememberReporting
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.uploads.UploadsScreen
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.UploadQueue
import io.github.solcott.activitytracker.data.activity.UploadSummary
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.QuotePosts
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.segments.GroupChoice
import io.github.solcott.activitytracker.data.segments.GroupPrefsRepository
import io.github.solcott.activitytracker.data.segments.SegmentNames
import io.github.solcott.activitytracker.data.segments.SegmentPrefsRepository
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.todayIn

@CircuitInject(FeedScreen::class, SessionScope::class)
@Composable
@Suppress("CyclomaticComplexMethod") // One branch per feed event.
fun FeedPresenter(
    navigator: Navigator,
    feed: FeedRepository,
    activities: ActivityRepository,
    profiles: ProfileRepository,
    locationTracker: LocationTracker,
    clock: Clock,
    segmentPrefs: SegmentPrefsRepository,
    groupPrefs: GroupPrefsRepository,
    uploads: UploadQueue,
    names: SegmentNames,
    reports: ReportRepository,
    blocks: BlockRepository,
    quotes: QuotePosts,
): FeedState {
    val viewerDid = profiles.did.value
    // Retained, so the scope and filter survive opening an activity and coming back.
    var scope by rememberRetained { mutableStateOf(FeedScope.Following) }
    var filter by rememberRetained { mutableStateOf(FeedFilter.Default) }
    // The control owns the live filter, so clearing from outside it re-seeds it with a new screen.
    var filterScreen by rememberRetained { mutableStateOf(FeedFilterScreen(filter)) }
    val zone = remember { TimeZones.device() }
    val query = remember(filter) { filter.toActivityFilter(clock.todayIn(zone), zone) }
    val mine = scope == FeedScope.Mine
    val uploadSummary by uploads.summary.collectAsState(UploadSummary())
    var error by remember { mutableStateOf<UiError?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var syncedFor by remember { mutableIntStateOf(-1) }
    val flights = rememberSingleFlight()
    // Keyed by the query, so a new filter starts from its own cache instead of showing the last filter's activities. A refresh restarts
    // the stream under the same state, so the activities stay listed while it runs and after it fails.
    val content =
        key(mine, query) {
            produceRetainedContentState<ImmutableList<TimelineItem>>(persistentListOf(), refreshKey) {
                feed
                    .feedOutcomes(mine, viewerDid, query) {
                        // The athlete's own writes go on the first load and on an explicit refresh, not on every filter change; an
                        // import's are left to the pacer, so the feed never waits for them (data/outbox).
                        if (syncedFor != refreshKey) {
                            activities.syncOwn()
                            syncedFor = refreshKey
                        }
                    }
                    // The athlete's votes on posts' proposals show at once (feed/posts).
                    .combine(names.changes) { outcome, _ -> outcome.mapData { items -> items.map(names::withChanges).toImmutableList() } }
            }
        }
    val activityEvents = rememberActivityListEventSink(navigator, activities) { error = it }
    val reporting =
        rememberReporting(reports) { author, _ ->
            flights.launch(FeedEvent.Post::class) {
                runSuspendCatching { blocks.setBlocked(author, true) }.onFailure { e -> error = uiErrorOf(UiError.Kind.UpdateBlock, e) }
            }
        }
    val quoting = rememberQuoting(quotes) { error = it }
    val postEvents = rememberPostEventSink(navigator, names, activities, reporting, quoting) { error = it }
    // Asked once: answering saves the setting, which hides the card here and on every device. A failed save leaves it to ask again.
    var askSegments by remember { mutableStateOf(false) }
    // The same for grouping, shown only once the segments card is answered, so the two never ask at once.
    var askGroups by remember { mutableStateOf(false) }

    // Apart from the feed load, which mustn't wait on them, and from each other: each is best effort and slow for a long history.
    LaunchedEffect(Unit) {
        launch { activities.repairZones() }
        launch { activities.repairRoutes() }
        launch { askSegments = segmentPrefs.shouldAsk() }
        launch { askGroups = groupPrefs.shouldAsk() }
    }

    val revealed = rememberRevealed()
    return FeedState(
        content,
        error,
        uploadSummary,
        viewerDid,
        locationTracker.isSupported,
        scope,
        Scopes,
        filter,
        filterScreen,
        revealed,
        askSegments,
        askGroups = askGroups && !askSegments,
        reporting = reporting,
        quote = quoting.fields,
    ) { event ->
        when (event) {
            FeedEvent.Refresh -> {
                refreshKey++
            }

            is FeedEvent.SetScope -> {
                scope = event.scope
            }

            is FeedEvent.FilterChanged -> {
                filter = event.filter
            }

            FeedEvent.ClearFilters -> {
                // One snapshot, so the feed never shows the cleared filter with the old control.
                Snapshot.withMutableSnapshot {
                    filter = FeedFilter.Default
                    // A new generation: a screen equal to the last would keep the control, and its narrowed chips.
                    filterScreen = FeedFilterScreen(FeedFilter.Default, filterScreen.generation + 1)
                }
            }

            is FeedEvent.Activity -> {
                activityEvents(event.event)
            }

            is FeedEvent.Post -> {
                postEvents(event.event)
            }

            is FeedEvent.OpenImportBatch -> {
                val author = event.card.author
                navigator.goTo(ImportBatchScreen(author.did.value, event.card.batch, author.shownName))
            }

            FeedEvent.Record -> {
                navigator.goTo(RecordScreen)
            }

            FeedEvent.AddManual -> {
                navigator.goTo(ManualActivityScreen)
            }

            FeedEvent.Import -> {
                navigator.goTo(ImportActivityScreen())
            }

            FeedEvent.OpenUploads -> {
                navigator.goTo(UploadsScreen)
            }

            is FeedEvent.AnswerSegments -> {
                flights.launch(FeedEvent.AnswerSegments::class) {
                    runSuspendCatching { segmentPrefs.save(event.on) }
                        .onSuccess { askSegments = false }
                        .onFailure { e -> error = uiErrorOf(UiError.Kind.SaveSettings, e) }
                }
            }

            is FeedEvent.AnswerGroups -> {
                if (event.choice != GroupChoice.NEVER_ASKED) {
                    flights.launch(FeedEvent.AnswerGroups::class) {
                        runSuspendCatching { groupPrefs.save(event.choice) }
                            .onSuccess { askGroups = false }
                            .onFailure { e -> error = uiErrorOf(UiError.Kind.SaveSettings, e) }
                    }
                }
            }
        }
    }
}

/** Every scope, until clubs make the list the viewer's. */
private val Scopes = FeedScope.entries.toImmutableList()
