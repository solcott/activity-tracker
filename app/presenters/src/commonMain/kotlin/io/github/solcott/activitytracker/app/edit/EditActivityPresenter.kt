package io.github.solcott.activitytracker.app.edit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.formContent
import io.github.solcott.activitytracker.app.profile.rememberApprovesFollowers
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.activity.ActivityEdit
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.segments.SegmentPrefsRepository
import io.github.solcott.activitytracker.data.segments.SegmentTrackStore
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.activityType
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.circuit.produceRetainedContentState

@CircuitInject(EditActivityScreen::class, SessionScope::class)
@Composable
@Suppress("LongParameterList") // Injected collaborators.
fun EditActivityPresenter(
    screen: EditActivityScreen,
    navigator: Navigator,
    feed: FeedRepository,
    manager: ActivityManager,
    defaults: ActivityDefaults,
    profiles: ProfileRepository,
    segmentPrefs: SegmentPrefsRepository,
    segmentTracks: SegmentTrackStore,
): EditActivityState {
    // Why a save failed; never a loading failure, which is [loaded]'s.
    var error by remember { mutableStateOf<UiError?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var loadKey by remember { mutableIntStateOf(0) }
    val loaded =
        produceRetainedContentState<EditActivityLoad?>(null, loadKey) {
            outcomeFlow {
                val view = feed.activity(AtUri(screen.uri))
                // Charts are extra: without them (or when they can't load) only the categories its summary has are offered.
                EditActivityLoad(view, runSuspendCatching { feed.streams(view.uri) }.getOrNull())
            }
        }
    val title = rememberTextFieldState()
    val form = rememberDetailsForm()
    val flights = rememberSingleFlight()
    // Read through state: the visibility holder is remembered once and asks for it as it changes.
    val approvesFollowers by rememberUpdatedState(rememberApprovesFollowers(profiles))
    val visibility = rememberEditVisibility(defaults.canChoose) { approvesFollowers }
    val exclusion = rememberLeaderboardExclusion(AtUri(screen.uri), segmentPrefs, segmentTracks)

    // The activity the form was filled from; later loads change neither.
    val shown = rememberEditSeed(loaded, title, form, visibility)
    val seededTitle = shown?.record?.title.orEmpty()

    fun save(current: ActivityView) {
        flights.launch(EditActivityEvent.Save) {
            error = null
            val edit =
                ActivityEdit(
                    title = title.text.toString(),
                    description = form.description.text.toString(),
                    diveSite = form.diveSite.text.toString().takeIf { current.record.sport == Sport.DIVE },
                    media = form.mediaEdits(),
                )
            runSuspendCatching {
                // An activity from before categories keeps its core charts with its one streams record; saving moves them to the
                // activity's tier, so a wider one is the athlete's call.
                if (visibility.asksAboutCoreCharts(current, manager)) return@runSuspendCatching false
                manager.edit(current, edit)
                visibility.changed?.let { manager.setVisibility(current, it) }
                exclusion.save()
                true
            }
                .onSuccess { saved -> if (saved) navigator.pop() }
                .onFailure { error = uiErrorOf(UiError.Kind.SaveActivity, it) }
        }
    }

    val isDive = shown?.record?.sport == Sport.DIVE
    return EditActivityState(
        content = loaded.formContent(seeded = shown != null),
        type = shown?.record?.activityType,
        title = title,
        details = shown?.let { form.fields(isDive) },
        saving = flights.isRunning(EditActivityEvent.Save),
        error = error,
        confirmDiscard = confirmDiscard,
        visibility = visibility.fields,
        confirmNarrowing = visibility.confirmingNarrowing,
        confirmCoreCharts = visibility.confirmingCoreCharts,
        leaderboardExclusion = exclusion.excluded.takeIf { shown != null },
    ) { event ->
        val saveLoaded = { shown?.let(::save) ?: Unit }
        when (event) {
            // Narrowing a Public activity can't recall what's already been published: the athlete is asked first.
            EditActivityEvent.Save -> visibility.save(saveLoaded)

            EditActivityEvent.ConfirmNarrowing -> visibility.narrowingAnswered(confirmed = true, saveLoaded)

            EditActivityEvent.CancelNarrowing -> visibility.narrowingAnswered(confirmed = false, saveLoaded)

            EditActivityEvent.ConfirmCoreCharts -> visibility.coreChartsAnswered(confirmed = true, saveLoaded)

            EditActivityEvent.CancelCoreCharts -> visibility.coreChartsAnswered(confirmed = false, saveLoaded)

            EditActivityEvent.Back -> {
                if (isChanged(shown, title.text.toString() != seededTitle, form.isChanged, visibility, exclusion)) confirmDiscard = true
                else navigator.pop()
            }

            EditActivityEvent.Discard -> {
                confirmDiscard = false
                navigator.pop()
            }

            EditActivityEvent.KeepEditing -> {
                confirmDiscard = false
            }

            EditActivityEvent.Retry -> {
                loadKey++
            }

            is EditActivityEvent.LeaderboardExclusionChanged -> {
                exclusion.set(event.excluded)
            }
        }
    }
}

/** Whether the loaded [activity] has unsaved changes to its title, its other details, who sees it or whether leaderboards rank it. */
private fun isChanged(
    activity: ActivityView?,
    title: Boolean,
    details: Boolean,
    visibility: EditVisibility,
    exclusion: LeaderboardExclusion,
): Boolean = activity != null && (title || details || visibility.changed != null || exclusion.changed)

/**
 * Fills [title], [form] and [visibility] from the first activity [loaded] answers, kept while the screen is: null until they're filled. The
 * title is saved with the screen, so it's filled once; the details and visibility holders aren't, so a recreated screen fills them again.
 */
@Composable
private fun rememberEditSeed(
    loaded: ContentState<EditActivityLoad?>,
    title: TextFieldState,
    form: DetailsForm,
    visibility: EditVisibility,
): ActivityView? {
    var seed by rememberRetained { mutableStateOf<EditActivityLoad?>(null) }
    var titleFilled by rememberSaveable { mutableStateOf(false) }
    var holdersFilled by remember { mutableStateOf(false) }
    val data = loaded.data
    SideEffect(data) {
        val load = seed ?: data ?: return@SideEffect
        val view = load.view
        if (!titleFilled) {
            title.setTextAndPlaceCursorAtEnd(view.record.title.orEmpty())
            titleFilled = true
        }
        if (!holdersFilled) {
            form.seed(view.record.description, view.record.dive?.site, existingMedia(view))
            visibility.seed(view, load.streams)
            holdersFilled = true
        }
        seed = load
    }
    return seed?.view?.takeIf { holdersFilled }
}

/** The activity being edited and, when they could be read, its streams: which detail categories it has data for. */
@Immutable internal class EditActivityLoad(val view: ActivityView, val streams: GetActivityStreams.Output?)
