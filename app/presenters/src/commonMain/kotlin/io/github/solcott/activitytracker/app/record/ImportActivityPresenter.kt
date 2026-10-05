package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.activityfile.FitActivityMapper
import io.github.solcott.activitytracker.activityfile.FitDecoder
import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@CircuitInject(ImportActivityScreen::class, SessionScope::class)
@Composable
fun ImportActivityPresenter(
    navigator: Navigator,
    activities: ActivityRepository,
    fitDecoder: FitDecoder,
): ImportActivityState {
    // The parsed file isn't saveable; after process death the athlete picks it again.
    var imported by remember { mutableStateOf<Pair<String, ImportedActivity>?>(null) }
    var sport by rememberSaveable { mutableStateOf(Sport.OTHER) }
    var title by rememberSaveable { mutableStateOf("") }
    var reading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val scope = rememberCoroutineScope()

    val preview = remember(imported) { imported?.let { (name, activity) -> activity.toPreview(name) } }

    return ImportActivityState(reading, preview, sport, title, saving, error) { event ->
        when (event) {
            is ImportActivityEvent.FilePicked -> {
                // Drop the previous file first, so a failed pick can't leave its preview (and Save) behind.
                imported = null
                if (!event.name.endsWith(".fit", ignoreCase = true)) {
                    error = UiError(UiError.Kind.UnsupportedActivityFile, null)
                    return@ImportActivityState
                }
                reading = true
                error = null
                scope.launch {
                    runSuspendCatching {
                        withContext(Dispatchers.Default) { FitActivityMapper.map(fitDecoder.decode(event.bytes.toByteArray())) }
                    }
                        .onSuccess {
                            imported = event.name to it
                            sport = it.sport
                        }
                        .onFailure { e -> error = UiError.of(UiError.Kind.ReadActivityFile, e) }
                    reading = false
                }
            }

            is ImportActivityEvent.SportChanged -> {
                sport = event.value
            }

            is ImportActivityEvent.TitleChanged -> {
                title = event.value
            }

            ImportActivityEvent.Back -> {
                navigator.pop()
            }

            ImportActivityEvent.Save -> {
                val activity = imported?.second ?: return@ImportActivityState
                saving = true
                scope.launch {
                    runSuspendCatching {
                        activities.publish(ActivityDraft.fromImport(activity, sport, title.ifBlank { null }))
                        navigator.pop()
                    }
                        .onFailure { e -> error = UiError.of(UiError.Kind.SaveActivity, e) }
                        .also { saving = false }
                }
            }
        }
    }
}

private fun ImportedActivity.toPreview(fileName: String) =
    ImportPreview(
        fileName = fileName,
        elapsed = elapsed,
        movingSeconds = movingSeconds,
        distanceMeters = distanceMeters,
        elevationGainMeters = elevationGainMeters,
        avgHeartRate = avgHeartRate,
        maxHeartRate = maxHeartRate,
        device = device,
        polyline = ActivityDraft.polylineOf(samples),
    )
