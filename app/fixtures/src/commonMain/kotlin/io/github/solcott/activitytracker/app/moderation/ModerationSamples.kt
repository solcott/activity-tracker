package io.github.solcott.activitytracker.app.moderation

import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.fakeActivity
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.textfield.textFieldStateOf

object ModerationSamples {
    private val activity = fakeActivity()

    val all: Map<String, ModerationState>
        get() =
            mapOf(
                SampleKind.LOADED to ModerationState(textFieldStateOf(), loading = false, subject = null, error = null, eventSink = {}),
                "activity" to
                    ModerationState(
                        textFieldStateOf(activity.uri.value),
                        loading = false,
                        subject = ModerationSubject.Activity(activity, Visibility.PUBLIC),
                        error = null,
                        eventSink = {},
                    ),
            )
}
