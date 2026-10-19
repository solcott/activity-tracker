package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.activitylist.ActivityListEvent
import io.github.solcott.activitytracker.app.activitylist.rememberActivityListEventSink
import io.github.solcott.activitytracker.app.edit.EditActivityScreen
import io.github.solcott.activitytracker.app.media.MediaGalleryScreen
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.profile.rememberApprovesFollowers
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.settings.TrainingZonesScreen
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityExporter
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.feed.withLikeOverrides
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CombinedActivity
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@CircuitInject(ActivityDetailScreen::class, SessionScope::class)
@Composable
fun ActivityDetailPresenter(
    screen: ActivityDetailScreen,
    navigator: Navigator,
    feed: FeedRepository,
    activities: ActivityRepository,
    profiles: ProfileRepository,
    localLikes: LocalLikes,
    manager: ActivityManager,
    exporter: ActivityExporter,
    units: UnitsRepository,
): ActivityDetailState {
    // The activity as loaded, and how far the viewer's comment actions have moved its count since: one value, so a reload replaces both.
    var loaded by remember { mutableStateOf<CountedActivity?>(null) }
    val activity = loaded?.view
    var error by remember { mutableStateOf<UiError?>(null) }
    var likeError by remember { mutableStateOf<UiError?>(null) }
    var commentError by remember { mutableStateOf<UiError?>(null) }
    val draft = rememberTextFieldState()
    val replying = remember { mutableStateOf<CommentView?>(null) }
    var loadKey by remember { mutableIntStateOf(0) }
    var showLikes by remember { mutableStateOf(false) }
    val flights = rememberSingleFlight()
    // The viewer's own like toggles, laid over the loaded activity: the AppView only reflects them once Jetstream catches up.
    val likeOverrides by localLikes.overrides.collectAsState()

    LaunchedEffect(loadKey) {
        error = null
        runSuspendCatching {
            loaded = CountedActivity(feed.activity(AtUri(screen.uri)))
        }
            .onFailure { error = UiError.of(UiError.Kind.LoadActivity, it) }
    }

    // An edit shows as soon as it's saved, and stays over the AppView's copy until Jetstream brings it there.
    val rewrite by remember(screen.uri) { feed.rewrites.observe(AtUri(screen.uri)) }.collectAsState(null)

    // An activity just written here (a combined one, say) isn't served until the AppView indexes it: try again for a while.
    AwaitIndexed(failed = error != null && loaded == null, written = rewrite != null) { loadKey++ }

    val unitSystem by units.units.collectAsState()
    val charts = rememberCharts(AtUri(screen.uri), loadKey, unitSystem, feed)
    val comments = rememberComments(AtUri(screen.uri), loadKey, feed, flights)
    // As served: the record without fields of narrower categories, and the details records of the tiers the viewer is admitted to.
    val served = loaded?.shown(rewrite?.let { r -> { a: ActivityView -> feed.rewrites.rewritten(a, r) } }, likeOverrides)
    // As shown: every detail the viewer was served on the record, with what they weren't left out.
    val shown = remember(served) { served?.let { it.copy(record = it.effectiveRecord()) } }
    val isOwn = shown?.author?.did == profiles.did
    val approvesFollowers = rememberApprovesFollowers(profiles, enabled = isOwn)
    val shownCharts = chartsFor(shown, charts.charts)
    val sections = shown?.let { detailSections(it, shownCharts) } ?: persistentListOf()
    val narrower = narrowerCategories(served?.takeIf { isOwn }, charts.streams)
    val page = rememberPage(shown, shownCharts, narrower)
    val manage = rememberManage(activity.takeIf { isOwn }, loadKey, feed, manager, navigator, flights) { loadKey++ }
    val ownRoute = rememberOwnRoute(activity?.uri?.takeIf { isOwn }, activities, flights, combined = activity?.record?.combined != null)
    val hasStreams = charts.charts != null
    val isDive = shown?.record?.sport == Sport.DIVE
    val formats = remember(ownRoute.track, hasStreams, isDive) { exportFormats(ownRoute.track, hasStreams, isDive) }
    val export = rememberExport(shown?.takeIf { isOwn }, formats, !charts.charts?.laps.isNullOrEmpty(), exporter, flights)

    // A like toggles as it does in a list, one at a time per activity.
    val likes = rememberActivityListEventSink(navigator, activities) { likeError = it }

    val sink: (ActivityDetailEvent) -> Unit = { event ->
        val current = activity
        when (event) {
            ActivityDetailEvent.Back -> {
                navigator.pop()
            }

            ActivityDetailEvent.Retry -> {
                loadKey++
            }

            ActivityDetailEvent.OpenAuthor,
            is ActivityDetailEvent.OpenProfile,
            is ActivityDetailEvent.OpenActivity,
            ActivityDetailEvent.OpenTrainingZones,
            ActivityDetailEvent.Edit,
            ActivityDetailEvent.Combine,
            is ActivityDetailEvent.OpenMedia -> navigator.open(event, screen.uri, current)

            ActivityDetailEvent.ToggleLikesList -> {
                showLikes = !showLikes
            }

            ActivityDetailEvent.ToggleLike -> {
                likeError = null
                current?.let { likes(ActivityListEvent.ToggleLike(it)) }
            }

            ActivityDetailEvent.LoadMoreComments,
            ActivityDetailEvent.RetryComments,
            ActivityDetailEvent.SendComment,
            is ActivityDetailEvent.ReplyTo,
            ActivityDetailEvent.CancelReply,
            is ActivityDetailEvent.LoadMoreReplies,
            is ActivityDetailEvent.DeleteComment,
            is ActivityDetailEvent.HideComment,
            is ActivityDetailEvent.UnhideComment -> {
                current?.let {
                    CommentActions(
                            draft,
                            flights,
                            activities,
                            comments,
                            replying,
                            { e -> commentError = e },
                        ) { moved ->
                            loaded = loaded?.let { l -> l.copy(commentMoves = l.commentMoves + moved) }
                        }
                        .on(event, it)
                }
            }

            is ActivityDetailEvent.Delete,
            ActivityDetailEvent.Ungroup,
            is ActivityDetailEvent.RemoveMember,
            ActivityDetailEvent.Split -> manage.on(event)

            ActivityDetailEvent.HideStart -> ownRoute.hide(true)

            ActivityDetailEvent.HideEnd -> ownRoute.hide(false)

            is ActivityDetailEvent.Export -> export.export(event.format)

            ActivityDetailEvent.DownloadOriginal -> export.downloadOriginal()

            ActivityDetailEvent.ExportHandedOff -> export.handedOff()
        }
    }

    val likesList = rememberLikesList(showLikes, AtUri(screen.uri), viewerGave = shown?.viewer?.like != null, feed, profiles)
    return detailState(shown, error, sink) {
        ActivityDetailState.Loaded(
            activity = it,
            comments = comments.paging,
            sentComments = comments.sent,
            draftComment = draft,
            replyingTo = replying.value,
            viewerDid = profiles.did,
            isOwn = isOwn,
            likesList = likesList,
            likeError = likeError,
            commentError = commentError,
            eventSink = sink,
            charts = shownCharts,
            poolLength = poolLength(it.record.poolLength),
            chartsMissingZones = charts.missingZones(isOwn),
            narrowerCategories = narrower,
            approvesFollowers = approvesFollowers,
            sections = sections,
            page = page,
            manage = manage.state,
            ownRoute = ownRoute.line?.polyline,
            ownRouteGaps = ownRoute.line?.gaps?.toImmutableList(),
            canHideRouteEnds = ownRoute.canHide,
            zoneAdded = ownRoute.zoneAdded,
            routeError = ownRoute.error,
            export = export.state,
        )
    }
}

/**
 * [view] as [FeedRepository.activity] loaded it, its comment count right for the viewer's comment changes then, and [commentMoves], how far
 * their sends, deletes, hides and unhides on this screen have moved it since.
 */
private data class CountedActivity(val view: ActivityView, val commentMoves: Int = 0) {
    /** [view] as it shows: [rewritten] with the viewer's own edit, their like toggles laid over it, and its count moved by theirs. */
    fun shown(rewritten: ((ActivityView) -> ActivityView)?, likeOverrides: Map<String, AtUri?>): ActivityView {
        val liked = (rewritten?.invoke(view) ?: view).withLikeOverrides(likeOverrides)
        return if (commentMoves == 0) liked else liked.copy(commentCount = (liked.commentCount + commentMoves).coerceAtLeast(0))
    }
}

/** The owner's delete, ungroup, split and member removal: [state] is null on someone else's activity. */
private class Manage(
    val state: ActivityManage?,
    val delete: (withRecordings: Boolean) -> Unit,
    val ungroup: () -> Unit,
    val removeMember: (AtUri) -> Unit = {},
    val split: () -> Unit = {},
) {
    fun on(event: ActivityDetailEvent) {
        when (event) {
            is ActivityDetailEvent.Delete -> delete(event.withRecordings)
            ActivityDetailEvent.Ungroup -> ungroup()
            is ActivityDetailEvent.RemoveMember -> removeMember(event.uri)
            ActivityDetailEvent.Split -> split()
            else -> Unit
        }
    }
}

/**
 * Deleting or ungrouping the signed-in athlete's own [activity] (null for someone else's). A recording loads the activity it records, to
 * tell whether the two are grouped alone. A delete leaves the screen; an ungroup stays and [reload]s it.
 */
@Composable
@Suppress("LongParameterList") // Injected collaborators.
private fun rememberManage(
    activity: ActivityView?,
    loadKey: Int,
    feed: FeedRepository,
    manager: ActivityManager,
    navigator: Navigator,
    flights: SingleFlight,
    reload: () -> Unit,
): Manage {
    var error by remember { mutableStateOf<UiError?>(null) }
    val recordingOf = activity?.record?.recordingOf
    // The main activity's recordings, this one among them; null until loaded, and when it's gone.
    var siblings by remember(recordingOf) { mutableStateOf<Int?>(null) }
    LaunchedEffect(recordingOf, loadKey) {
        if (recordingOf != null) {
            runSuspendCatching { feed.activity(recordingOf) }.onSuccess { siblings = it.recordings.orEmpty().size }
        }
    }
    val delete = ActivityDetailEvent.Delete::class
    val ungroup = ActivityDetailEvent.Ungroup
    val split = ActivityDetailEvent.Split
    val remove = ActivityDetailEvent.RemoveMember::class
    activity ?: return Manage(null, {}, {})
    val recordings = activity.recordings.orEmpty()
    val members = activity.record.combined?.members.orEmpty()
    val state =
        ActivityManage(
            otherRecordings = recordings.size,
            canUngroup = if (recordingOf == null) recordings.size == 1 else siblings == 1,
            members = members.size,
            canCombine = activity.combinedInto == null && members.size < CombinedActivity.MAX_MEMBERS,
            busy = flights.isRunning(delete) || flights.isRunning(ungroup) || flights.isRunning(split) || flights.isRunning(remove),
            error = error,
        )
    return Manage(
        state,
        delete = { withRecordings ->
            flights.launch(delete) {
                error = null
                runSuspendCatching { manager.delete(activity, withRecordings) }
                    .onSuccess { navigator.pop() }
                    .onFailure { error = UiError.of(UiError.Kind.DeleteActivity, it) }
            }
        },
        ungroup = {
            if (state.canUngroup) {
                flights.launch(ungroup) {
                    error = null
                    runSuspendCatching {
                        val visibility = activity.visibilityOrPublic
                        if (recordingOf == null) manager.ungroup(recordings.single().uri, from = activity.uri, visibility)
                        else manager.ungroup(activity.uri, from = recordingOf, visibility)
                    }
                        .onSuccess { reload() }
                        .onFailure { error = UiError.of(UiError.Kind.UngroupActivity, it) }
                }
            }
        },
        removeMember = { member ->
            flights.launch(remove) {
                error = null
                runSuspendCatching { manager.removeMember(activity, member) }
                    // Taking out one of two members splits the combined activity, which is then gone.
                    .onSuccess { if (members.size - 1 < CombinedActivity.MIN_MEMBERS) navigator.pop() else reload() }
                    .onFailure { error = UiError.of(UiError.Kind.SplitActivity, it) }
            }
        },
        split = {
            flights.launch(split) {
                error = null
                runSuspendCatching { manager.split(activity) }
                    .onSuccess { navigator.pop() }
                    .onFailure { error = UiError.of(UiError.Kind.SplitActivity, it) }
            }
        },
    )
}

/** The sections [activity] has data for: one per kind of chart, and the device. */
internal fun detailSections(activity: ActivityView, charts: ActivityCharts?): ImmutableList<DetailSection> =
    detailSections(activity.record, charts)

/** The sections [record] has data for: one per kind of chart, and the device. */
internal fun detailSections(record: ActivityRecord, charts: ActivityCharts?): ImmutableList<DetailSection> {
    val kinds = charts?.lines.orEmpty().map { it.kind }.toSet()
    return DetailSection.entries
        .filter { section ->
            when (section) {
                DetailSection.HeartRate -> StreamKind.HeartRate in kinds
                DetailSection.Power -> StreamKind.Power in kinds
                DetailSection.CyclingDynamics -> CYCLING_DYNAMICS.any { it in kinds }
                DetailSection.Speed -> StreamKind.Speed in kinds
                DetailSection.Cadence -> StreamKind.Cadence in kinds
                DetailSection.Elevation -> StreamKind.Elevation in kinds || StreamKind.Grade in kinds
                DetailSection.Temperature -> StreamKind.Temperature in kinds
                DetailSection.Physiology -> PHYSIOLOGY.any { it in kinds }
                DetailSection.Depth -> StreamKind.Depth in kinds
                DetailSection.Oxygen -> StreamKind.PpO2 in kinds || StreamKind.Cns in kinds
                DetailSection.TankPressure -> !charts?.tankPressures.isNullOrEmpty()
                DetailSection.Respiration -> StreamKind.RespirationRate in kinds
                DetailSection.RunningDynamics -> RUNNING_DYNAMICS.any { it in kinds }
                DetailSection.EBike -> E_BIKE.any { it in kinds }
                DetailSection.Gears -> charts != null && charts.gears.size >= 2
                DetailSection.Legs -> !charts?.legs.isNullOrEmpty()
                DetailSection.Lengths -> !charts?.lengths.isNullOrEmpty()
                DetailSection.Sets -> !charts?.sets.isNullOrEmpty()
                DetailSection.Laps -> !charts?.laps.isNullOrEmpty()
                DetailSection.Device -> record.device != null || !record.sensors.isNullOrEmpty()
            }
        }
        .toImmutableList()
}

/** [charts] as [view] shows them: a ski day's count its runs. Null until both have loaded. */
private fun chartsFor(view: ActivityView?, charts: ActivityCharts?): ActivityCharts? =
    if (view == null || charts == null) null else ChartsBuilder.forSport(charts, view.record.sport)

/** A pool length of [centimeters] in whole meters, or in whole yards when it's a yard pool; null for none. */
internal fun poolLength(centimeters: Int?): PoolLength? {
    centimeters?.takeIf { it > 0 } ?: return null
    val yards = centimeters / CM_PER_YARD
    val wholeYards = yards.roundToInt()
    return if (centimeters % CM_PER_M != 0 && abs(yards - wholeYards) < YARD_TOLERANCE) PoolLength(wholeYards, yards = true)
    else PoolLength((centimeters / CM_PER_M.toDouble()).roundToInt(), yards = false)
}

private const val CM_PER_M = 100
private const val CM_PER_YARD = 91.44
private const val YARD_TOLERANCE = 0.02

/** The lines the running dynamics section charts. */
internal val RUNNING_DYNAMICS =
    setOf(
        StreamKind.VerticalOscillation,
        StreamKind.StanceTime,
        StreamKind.StanceTimeBalance,
        StreamKind.StepLength,
        StreamKind.VerticalRatio,
        StreamKind.FormPower,
        StreamKind.LegSpringStiffness,
    )

internal val CYCLING_DYNAMICS = setOf(StreamKind.TorqueEffectiveness, StreamKind.PedalSmoothness, StreamKind.PlatformCentreOffset)

internal val PHYSIOLOGY = setOf(StreamKind.CoreTemperature, StreamKind.SmO2, StreamKind.TotalHemoglobin)

internal val E_BIKE = setOf(StreamKind.EbikeBattery, StreamKind.EbikeAssistMode, StreamKind.EbikeAssistLevel)

/** The activity's charts, and the owner's control over who sees them. */
private class Charts(val charts: ActivityCharts?, val streams: GetActivityStreams.Output?) {
    /** The [own] activity has heart rate or power but no zones to split it into. */
    fun missingZones(own: Boolean): Boolean = own && charts?.missingZones() == true
}

/**
 * [activity]'s charts, reloaded with [loadKey], from its streams merged from every tier the viewer is admitted to. They're extra: without
 * streams, or when they fail to load, the activity shows without them.
 */
@Composable
private fun rememberCharts(activity: AtUri, loadKey: Int, units: UnitSystem, feed: FeedRepository): Charts {
    var streams by remember { mutableStateOf<GetActivityStreams.Output?>(null) }
    var charts by remember { mutableStateOf<ActivityCharts?>(null) }
    // The lap table is in the viewer's units, so a change of units rebuilds it.
    LaunchedEffect(loadKey, units) {
        runSuspendCatching { feed.streams(activity) }
            .onSuccess { loaded ->
                streams = loaded
                charts = loaded?.let { ChartsBuilder.buildOffMain(it, units) }
            }
    }
    return Charts(charts, streams)
}

/** Heart rate or power without the zones to split it into. */
private fun ActivityCharts.missingZones(): Boolean =
    lines.any { it.kind == StreamKind.HeartRate } && heartRateZones == null ||
        lines.any { it.kind == StreamKind.Power } && powerZones == null

private fun detailState(
    activity: ActivityView?,
    error: UiError?,
    sink: (ActivityDetailEvent) -> Unit,
    loaded: (ActivityView) -> ActivityDetailState.Loaded,
): ActivityDetailState =
    when {
        activity != null -> loaded(activity)
        error != null -> ActivityDetailState.Error(error, sink)
        else -> ActivityDetailState.Loading(sink)
    }

/**
 * Who liked [uri] while [show] is set, reloaded each time it's shown. The AppView lags the viewer's own toggles, so the viewer is added or
 * dropped to match [viewerGave].
 */
@Composable
private fun rememberLikesList(
    show: Boolean,
    uri: AtUri,
    viewerGave: Boolean,
    feed: FeedRepository,
    profiles: ProfileRepository,
): LikesList? {
    var athletes by remember { mutableStateOf(emptyList<ProfileViewBasic>()) }
    // Starts (and goes back to) loading while hidden, so the first frame after showing doesn't flash an empty list.
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var me by remember { mutableStateOf<ProfileViewBasic?>(null) }

    LaunchedEffect(show) {
        loading = true
        error = null
        if (!show) return@LaunchedEffect
        runSuspendCatching { athletes = feed.likes(uri) }.onFailure { error = UiError.of(UiError.Kind.LoadLikes, it) }
        loading = false
    }

    val viewer = profiles.did
    val viewerListed = athletes.any { it.did == viewer }
    val needMe = show && !loading && error == null && viewerGave && !viewerListed && me == null
    LaunchedEffect(needMe) {
        if (needMe) {
            runSuspendCatching { profiles.profile(viewer.value) }.onSuccess { me = ProfileViewBasic(it.did, it.handle, it.displayName) }
        }
    }

    if (!show) return null
    val merged =
        when {
            !viewerGave -> athletes.filter { it.did != viewer }
            viewerListed -> athletes
            else -> listOfNotNull(me) + athletes
        }
    return LikesList(merged.toImmutableList(), loading, error)
}

/** Goes where [event] leads from the activity at [uri]; [current] is the activity, once loaded. */
private fun Navigator.open(event: ActivityDetailEvent, uri: String, current: ActivityView?) {
    when (event) {
        ActivityDetailEvent.OpenAuthor -> current?.let { goTo(ProfileScreen(it.author.did.value)) }
        is ActivityDetailEvent.OpenProfile -> goTo(ProfileScreen(event.did.value))
        is ActivityDetailEvent.OpenActivity -> goTo(ActivityDetailScreen(event.uri.value))
        ActivityDetailEvent.OpenTrainingZones -> goTo(TrainingZonesScreen)
        ActivityDetailEvent.Edit -> goTo(EditActivityScreen(uri))
        is ActivityDetailEvent.OpenMedia -> goTo(MediaGalleryScreen(uri, event.index))
        ActivityDetailEvent.Combine -> goTo(CombinePickerScreen(uri))
        else -> Unit
    }
}
