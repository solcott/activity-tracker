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
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.activityfile.UnsupportedActivityFileException
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlinx.coroutines.launch

@CircuitInject(ImportActivityScreen::class, SessionScope::class)
@Composable
fun ImportActivityPresenter(
    navigator: Navigator,
    activities: ActivityRepository,
    reader: ActivityFileReader,
    recentTypes: RecentActivityTypes,
): ImportActivityState {
    // The parsed file isn't saveable; after process death the athlete picks it again.
    var imported by remember { mutableStateOf<Pair<String, ImportedActivity>?>(null) }
    var type by rememberSaveable(stateSaver = ActivityTypeSaver) { mutableStateOf(ActivityType(Sport.OTHER)) }
    var title by rememberSaveable { mutableStateOf("") }
    var reading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val scope = rememberCoroutineScope()

    val preview = remember(imported) { imported?.let { (name, activity) -> activity.toPreview(name) } }
    val quickTypes = quickActivityTypes(recentTypes)

    return ImportActivityState(reading, preview, type, quickTypes, title, saving, error) { event ->
        when (event) {
            is ImportActivityEvent.FilePicked -> {
                // Drop the previous file first, so a failed pick can't leave its preview (and Save) behind.
                imported = null
                reading = true
                error = null
                scope.launch {
                    // The format comes from the file's content, not its name.
                    runSuspendCatching { reader.read(event.bytes.toByteArray()) }
                        .onSuccess {
                            imported = event.name to it
                            type = it.type
                        }
                        .onFailure { e ->
                            error =
                                if (e is UnsupportedActivityFileException) UiError(UiError.Kind.UnsupportedActivityFile, null)
                                else UiError.of(UiError.Kind.ReadActivityFile, e)
                        }
                    reading = false
                }
            }

            is ImportActivityEvent.TypeChanged -> {
                type = event.value
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
                        activities.publish(ActivityDraft.fromImport(activity, type, title.ifBlank { null }))
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
