package io.github.solcott.activitytracker.app.edit

import androidx.compose.foundation.text.input.TextFieldState
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.persistentListOf

object EditActivitySamples {
    private fun state(content: ContentState<Unit>, loaded: Boolean = true) =
        EditActivityState(
            content = content,
            type = if (loaded) ActivityType(Sport.RIDE) else null,
            title = TextFieldState(if (loaded) "Morning Ride" else ""),
            details =
                if (loaded) {
                    ActivityDetailsFields(TextFieldState(), diveSite = null, media = persistentListOf(), maxMedia = 4, eventSink = {})
                } else {
                    null
                },
            saving = false,
            error = null,
            confirmDiscard = false,
            visibility = if (loaded) VisibilityFields() else null,
            eventSink = {},
        )

    val all: Map<String, EditActivityState>
        get() =
            mapOf(
                SampleKind.LOADING to state(ContentStates.loading(Unit), loaded = false),
                SampleKind.FAILED to state(ContentStates.failed(Unit), loaded = false),
                SampleKind.LOADED to state(ContentStates.loaded(Unit)),
            )
}
