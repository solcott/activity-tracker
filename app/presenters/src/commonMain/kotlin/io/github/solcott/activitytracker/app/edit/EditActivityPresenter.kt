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
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityEdit
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.activityType

@CircuitInject(EditActivityScreen::class, SessionScope::class)
@Composable
fun EditActivityPresenter(
    screen: EditActivityScreen,
    navigator: Navigator,
    feed: FeedRepository,
    manager: ActivityManager,
): EditActivityState {
    var activity by remember { mutableStateOf<ActivityView?>(null) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var loadKey by remember { mutableIntStateOf(0) }
    val title = rememberTextFieldState()
    var seededTitle by remember { mutableStateOf("") }
    val form = rememberDetailsForm()
    val flights = rememberSingleFlight()

    LaunchedEffect(loadKey) {
        if (activity != null) return@LaunchedEffect
        error = null
        runSuspendCatching { feed.activity(AtUri(screen.uri)).activity }
            .onSuccess { view ->
                seededTitle = view.record.title.orEmpty()
                title.setTextAndPlaceCursorAtEnd(seededTitle)
                form.seed(view.record.description, view.record.dive?.site, existingMedia(view))
                activity = view
            }
            .onFailure { error = UiError.of(UiError.Kind.LoadActivity, it) }
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
    ) { event ->
        when (event) {
            EditActivityEvent.Save -> {
                val current = activity ?: return@EditActivityState
                flights.launch(EditActivityEvent.Save) {
                    error = null
                    val edit =
                        ActivityEdit(
                            title = title.text.toString(),
                            description = form.description.text.toString(),
                            diveSite = form.diveSite.text.toString().takeIf { isDive },
                            media = form.mediaEdits(),
                        )
                    runSuspendCatching { manager.edit(current, edit) }
                        .onSuccess { navigator.pop() }
                        .onFailure { error = UiError.of(UiError.Kind.SaveActivity, it) }
                }
            }

            EditActivityEvent.Back -> {
                if (activity != null && (title.text.toString() != seededTitle || form.isChanged)) confirmDiscard = true else navigator.pop()
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
