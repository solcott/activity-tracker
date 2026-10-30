package io.github.solcott.activitytracker.app.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.mapContent
import io.github.solcott.activitytracker.app.moderation.rememberRevealed
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.report.Reporting
import io.github.solcott.activitytracker.app.report.ReportingEvent
import io.github.solcott.activitytracker.app.report.rememberReporting
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.moderation.BlurReason
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.collections.immutable.persistentListOf

@CircuitInject(MediaGalleryScreen::class, SessionScope::class)
@Composable
@Suppress("LongParameterList") // The gallery's repositories, and the navigator.
fun MediaGalleryPresenter(
    screen: MediaGalleryScreen,
    navigator: Navigator,
    feed: FeedRepository,
    profiles: ProfileRepository,
    blocks: BlockRepository,
    reports: ReportRepository,
): MediaGalleryState {
    val uri = AtUri(screen.uri)
    var loadKey by remember { mutableIntStateOf(0) }
    // Retained, so coming back to the gallery shows the photos at once; a retry reloads under them.
    val loaded = produceRetainedContentState<ActivityView?>(null, uri, loadKey) { feed.activityOutcome(uri) }
    // Why blocking the author failed; never a loading failure, which is [loaded]'s.
    var error by remember { mutableStateOf<UiError?>(null) }
    var page by rememberSaveable { mutableStateOf(screen.index) }
    var zoomed by rememberSaveable { mutableStateOf(false) }
    // Photos the athlete just saved show here too, before the AppView has them.
    val rewrite by remember(uri) { feed.rewrites.observe(uri) }.collectAsState(null)

    val shown = loaded.data?.let { a -> rewrite?.let { feed.rewrites.rewritten(a, it) } ?: a }
    val items = remember(shown) { shown?.mediaItems() ?: persistentListOf() }
    val flights = rememberSingleFlight()
    val reporting =
        rememberReporting(reports) { author, _ ->
            flights.launch(author) {
                runSuspendCatching { blocks.setBlocked(author, true) }.onFailure { error = uiErrorOf(UiError.Kind.UpdateBlock, it) }
            }
        }
    val shownPage = page.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val canReport = shown != null && shown.author.did != profiles.did && items.isNotEmpty()
    val (takenDown, blur) = shown.moderation(profiles.did)
    val revealed = rememberRevealed()
    return MediaGalleryState(
        loaded.mapContent { items },
        shownPage,
        zoomed,
        error,
        canReport,
        reporting,
        blur = blur,
        takenDown = takenDown,
        revealed = revealed,
    ) { event ->
        when (event) {
            is MediaGalleryEvent.PageChanged -> {
                page = event.page
                zoomed = false
            }

            // A blurred photo reveals first; zoom only once it's seen.
            MediaGalleryEvent.ToggleZoom ->
                if (blur != null && screen.uri !in revealed) revealed.eventSink(screen.uri) else zoomed = !zoomed

            MediaGalleryEvent.Back -> if (zoomed) zoomed = false else navigator.pop()

            MediaGalleryEvent.ReportPhoto -> shown?.takeIf { canReport }?.let { reporting.reportPhoto(it, shownPage) }

            MediaGalleryEvent.Retry -> loadKey++
        }
    }
}

/** Opens the report sheet for the photo at [page] of [activity]: the report names the activity and says which photo, counting from 1. */
private fun Reporting.reportPhoto(activity: ActivityView, page: Int) {
    val target = ReportTarget.Photo(ReportTarget.Activity(activity.uri, activity.cid, activity.author.did), page + 1)
    eventSink(ReportingEvent.Open(target, activity.author.displayName ?: activity.author.handle.value))
}

/** Whether [this], shown to [viewer], is their own taken-down activity, and its photos' media label. */
private fun ActivityView?.moderation(viewer: Did): Pair<Boolean, BlurReason?> {
    if (this == null) return false to null
    val decision = ModerationDecision.of(labels, author.labels, isOwn = author.did == viewer)
    return (decision == ModerationDecision.Takedown) to ModerationDecision.mediaOf(labels, author.labels)
}
