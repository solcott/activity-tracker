package io.github.solcott.activitytracker.app.edit

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.activity.ActivityEdit
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.activityType

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
): EditActivityState {
    var activity by remember { mutableStateOf<ActivityView?>(null) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var loadKey by remember { mutableIntStateOf(0) }
    val title = rememberTextFieldState()
    var seededTitle by remember { mutableStateOf("") }
    val form = rememberDetailsForm()
    val flights = rememberSingleFlight()
    val visibility = rememberEditVisibility(defaults.canChoose)

    LaunchedEffect(loadKey) {
        if (activity != null) return@LaunchedEffect
        error = null
        runSuspendCatching {
            val view = feed.activity(AtUri(screen.uri))
            // Charts are extra: without them (or when they can't load) only the categories its summary has are offered.
            val streams = runSuspendCatching { feed.streams(view.uri) }.getOrNull()
            view to streams
        }
            .onSuccess { (view, streams) ->
                seededTitle = view.record.title.orEmpty()
                title.setTextAndPlaceCursorAtEnd(seededTitle)
                form.seed(view.record.description, view.record.dive?.site, existingMedia(view))
                visibility.seed(view, streams)
                activity = view
            }
            .onFailure { error = UiError.of(UiError.Kind.LoadActivity, it) }
    }
    LaunchedEffect(Unit) {
        runSuspendCatching { profiles.ownProfileRecord() }
            .onSuccess { visibility.approvesFollowers = it?.followPolicy == FollowPolicy.APPROVE }
    }

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
                true
            }
                .onSuccess { saved -> if (saved) navigator.pop() }
                .onFailure { error = uiErrorOf(UiError.Kind.SaveActivity, it) }
        }
    }

    val loaded = activity
    val isDive = loaded?.record?.sport == Sport.DIVE
    return EditActivityState(
        type = loaded?.record?.activityType,
        title = title,
        details = loaded?.let { form.fields(isDive) },
        saving = flights.isRunning(EditActivityEvent.Save),
        error = error,
        confirmDiscard = confirmDiscard,
        visibility = visibility.fields,
        confirmNarrowing = visibility.confirmingNarrowing,
        confirmCoreCharts = visibility.confirmingCoreCharts,
    ) { event ->
        val saveLoaded = { activity?.let(::save) ?: Unit }
        when (event) {
            // Narrowing a Public activity can't recall what's already been published: the athlete is asked first.
            EditActivityEvent.Save -> visibility.save(saveLoaded)

            EditActivityEvent.ConfirmNarrowing -> visibility.narrowingAnswered(confirmed = true, saveLoaded)

            EditActivityEvent.CancelNarrowing -> visibility.narrowingAnswered(confirmed = false, saveLoaded)

            is EditActivityEvent.VisibilityChanged -> visibility.activityChanged(event.visibility)

            is EditActivityEvent.CategoryVisibilityChanged -> visibility.categoryChanged(event.category, event.visibility)

            EditActivityEvent.ConfirmCoreCharts -> visibility.coreChartsAnswered(confirmed = true, saveLoaded)

            EditActivityEvent.CancelCoreCharts -> visibility.coreChartsAnswered(confirmed = false, saveLoaded)

            EditActivityEvent.Back -> {
                if (isChanged(activity, title.text.toString() != seededTitle, form.isChanged, visibility)) confirmDiscard = true
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
        }
    }
}

/** Whether the loaded [activity] has unsaved changes to its title, its other details or who sees it. */
private fun isChanged(activity: ActivityView?, title: Boolean, details: Boolean, visibility: EditVisibility): Boolean =
    activity != null && (title || details || visibility.changed != null)
