package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.activitytracker.activityfile.TimeZones
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.edit.PickedPhoto
import io.github.solcott.activitytracker.app.edit.toNewPhoto
import io.github.solcott.activitytracker.app.launchAction
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.visibility.VisibilityTexts
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.HeldPhotos
import io.github.solcott.activitytracker.data.activity.photoCid
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.segments.ReportPhoto
import io.github.solcott.activitytracker.data.segments.TrailReports
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.TrailCondition
import io.github.solcott.activitytracker.lexicon.app.TrailReportRecord
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.activitytracker.textfield.TextFieldState
import io.github.solcott.activitytracker.textfield.clearText
import io.github.solcott.activitytracker.textfield.rememberTextFieldState
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * [segment]'s trail reports as its page shows them (segments/trail-reports), with the sink for every [ReportEvent]: the Conditions card,
 * the report sheet, adding photos (new ones, or the viewer's own from [efforts]' activities), the share question and the post. The viewer's
 * own reports show through [reports]' overlay on the host's read, so [segment] is already the segment as they changed it. [goTo] opens the
 * reports list and the photo viewer.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList") // An event per action on the sheet and the photos.
fun rememberReportFields(
    segment: SegmentView?,
    efforts: ImmutableList<EffortView>,
    reports: TrailReports,
    feed: FeedRepository,
    clock: Clock,
    goTo: (Screen) -> Unit,
): ReportFields {
    val flights = rememberSingleFlight()
    val zone = remember { TimeZones.device() }
    var open by rememberRetained { mutableStateOf(false) }
    var tags by rememberRetained { mutableStateOf<Set<String>>(emptySet()) }
    val note: TextFieldState = rememberTextFieldState()
    var photos by rememberRetained { mutableStateOf<List<Picked>>(emptyList()) }
    var adding by rememberRetained { mutableStateOf(false) }
    var ticked by rememberRetained { mutableStateOf<Set<String>>(emptySet()) }
    var confirming by rememberRetained { mutableStateOf(false) }
    var posting by rememberRetained { mutableStateOf(false) }
    var error by rememberRetained { mutableStateOf<UiError?>(null) }
    var nextKey by rememberRetained { mutableIntStateOf(0) }
    val rides = rememberRides(adding, efforts, feed)

    fun reset() {
        open = false
        tags = emptySet()
        note.clearText()
        photos = emptyList()
        adding = false
        ticked = emptySet()
        confirming = false
    }

    fun post(view: SegmentView) {
        val chosen = tags
        val text = note.text.toString()
        val sent = photos.map { it.photo }
        posting = true
        error = null
        flights.launchAction(ReportEvent.Post, UiError.Kind.PostTrailReport, onError = { error = it }) {
            try {
                reports.post(StrongRef(view.uri, view.cid), chosen, text, sent)
                reset()
            } finally {
                posting = false
            }
        }
    }

    val full = photos.size + ticked.size >= TrailReportRecord.MAX_IMAGES
    val shownRides = rides.map { ride ->
        RidePhotos(
            ride.title,
            ride.visibility,
            ride.picks
                .map {
                    RidePhoto(
                        it.key,
                        it.choice.url.orEmpty(),
                        it.choice.alt,
                        selected = it.key in ticked,
                        enabled = it.key in ticked || !full,
                    )
                }
                .toImmutableList(),
        )
    }

    return ReportFields(
        card = remember(segment, reports.self) { TrailReportTexts.card(segment, clock.now(), zone, canWrite = true) },
        sheet =
            segment
                ?.takeIf { open }
                ?.let { view ->
                    ReportSheet(
                        subtitle = TrailReportTexts.subtitle(view, clock.now(), zone),
                        surface = TrailReportTexts.toggles(TrailCondition.surface, tags),
                        onTrail = TrailReportTexts.toggles(TrailCondition.onTrail, tags),
                        note = note,
                        noteCount = TrailReportTexts.noteCount(TrailReportTexts.noteLength(note.text.toString())),
                        noteFits = TrailReportTexts.noteFits(note.text.toString()),
                        photos = photos.map { it.choice }.toImmutableList(),
                        posting = posting,
                    )
                },
        addPhotos =
            if (adding)
                AddPhotos(shownRides.toImmutableList(), photos.size + ticked.size, loading = rides.isEmpty() && efforts.isNotEmpty())
            else null,
        confirmingShare = confirming,
        error = error,
    ) { event ->
        val view = segment ?: return@ReportFields
        when (event) {
            ReportEvent.Open -> if (view.viewer?.canReportConditions == true) open = true
            ReportEvent.Close -> reset()
            is ReportEvent.Toggle ->
                if (event.value in TrailCondition.all) tags = if (event.value in tags) tags - event.value else tags + event.value
            is ReportEvent.AddPicked -> {
                val room = TrailReportRecord.MAX_IMAGES - photos.size
                photos = photos + event.photos.take(room.coerceAtLeast(0)).map { picked -> Picked.new("n${nextKey++}", picked) }
            }
            is ReportEvent.RemovePhoto -> photos = photos.filterNot { it.key == event.key }
            ReportEvent.OpenAddPhotos -> {
                if (view.viewer?.canReportConditions != true) return@ReportFields
                open = true
                adding = true
                ticked = emptySet()
            }
            is ReportEvent.TogglePhoto ->
                ticked =
                    when {
                        event.key in ticked -> ticked - event.key
                        photos.size + ticked.size < TrailReportRecord.MAX_IMAGES -> ticked + event.key
                        else -> ticked
                    }
            ReportEvent.AddTicked -> {
                val chosen = rides.flatMap { it.picks }.filter { it.key in ticked && photos.none { p -> p.key == it.key } }
                photos = (photos + chosen).take(TrailReportRecord.MAX_IMAGES)
                adding = false
                ticked = emptySet()
            }
            ReportEvent.CloseAddPhotos -> {
                adding = false
                ticked = emptySet()
            }
            ReportEvent.Post ->
                when {
                    posting || (tags.isEmpty() && photos.isEmpty()) || !TrailReportTexts.noteFits(note.text.toString()) -> Unit
                    // A public copy of a photo from a ride that isn't public is asked about every time (segments/trail-reports).
                    photos.any { it.choice.fromNonPublic } -> confirming = true
                    else -> post(view)
                }
            is ReportEvent.ConfirmShare -> {
                confirming = false
                if (event.share) post(view)
            }
            ReportEvent.OpenReports -> goTo(TrailReportsScreen(view.uri.value, view.currentName))
            is ReportEvent.OpenPhoto -> goTo(TrailPhotosScreen(segment = view.uri.value, start = event.index))
            ReportEvent.DismissError -> error = null
        }
    }
}

/** A photo in the sheet: what's posted and how it shows. */
internal data class Picked(val key: String, val photo: ReportPhoto, val choice: ReportPhotoChoice) {
    companion object {
        fun new(key: String, picked: PickedPhoto): Picked {
            return Picked(key, ReportPhoto.New(picked.toNewPhoto()), ReportPhotoChoice(key, null, null))
        }
    }
}

/** One of the viewer's rides with photos: its [title], [visibility] badge and what each photo posts as. */
internal data class RideSource(val title: AppText, val visibility: AppText, val picks: List<Picked>)

/**
 * The viewer's own activities behind [efforts], newest first and at most [RIDES_OFFERED], with their photos, read at once while [adding];
 * each photo keyed by its blob CID and shown from the AppView's view of that blob. An activity that fails to load is left out.
 */
@Composable
private fun rememberRides(adding: Boolean, efforts: ImmutableList<EffortView>, feed: FeedRepository): List<RideSource> {
    var rides by rememberRetained { mutableStateOf<List<RideSource>>(emptyList()) }
    val activities = remember(efforts) { efforts.sortedByDescending { it.startedOn }.map { it.activity }.distinct().take(RIDES_OFFERED) }
    LaunchedEffect(adding, activities) {
        if (!adding) return@LaunchedEffect
        rides =
            coroutineScope {
                activities.map { uri -> async { runSuspendCatching { feed.activity(uri) }.getOrNull()?.let(::rideOf) } }.awaitAll()
            }
                .filterNotNull()
    }
    return rides
}

/** [activity] as a ride to share photos from, or null when it has none. */
private fun rideOf(activity: ActivityView): RideSource? {
    val images = activity.record.media.orEmpty().filterIsInstance<ActivityMedia.Image>()
    if (images.isEmpty()) return null
    // The AppView may leave out a photo it doesn't serve, so its views are matched by blob CID, never by position.
    val urls = activity.media.orEmpty().filterIsInstance<ActivityMediaView.Image>().associate { photoCid(it.fullsize) to it.fullsize }
    val visibility = activity.visibilityOrPublic
    val title = activity.record.title?.takeIf { it.isNotBlank() }?.let { AppText.written(it) } ?: AppText.written(activity.record.sport)
    return RideSource(
        title = title,
        visibility = VisibilityTexts.badge(visibility) ?: AppText.named("visibility_public"),
        picks =
            images.map { image ->
                val key = image.image.ref.link
                val shared = ReportPhoto.Shared(image, visibility)
                Picked(key, shared, ReportPhotoChoice(key, urls[key] ?: HeldPhotos.url(key), image.alt, shared.fromNonPublic))
            },
    )
}

/** How many of the viewer's latest rides on the segment the photo picker offers. */
private const val RIDES_OFFERED = 10
