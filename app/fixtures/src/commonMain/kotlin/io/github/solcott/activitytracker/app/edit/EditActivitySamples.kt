package io.github.solcott.activitytracker.app.edit

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.media.MediaItem
import io.github.solcott.activitytracker.app.record.FormSamples
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.persistentListOf

object EditActivitySamples {
    private fun state(content: ContentState<Unit>, loaded: Boolean = true) =
        EditActivityState(
            content = content,
            type = if (loaded) ActivityType(Sport.RIDE) else null,
            title = textFieldStateOf(if (loaded) "Morning Ride" else ""),
            details =
                if (loaded) {
                    ActivityDetailsFields(textFieldStateOf(), diveSite = null, media = persistentListOf(), maxMedia = 4, eventSink = {})
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

    /** A ride with everything the form offers: a description, a saved and a picked photo, gear, its categories and the leaderboards. */
    private val full: EditActivityState
        get() =
            state(ContentStates.loaded(Unit))
                .copy(
                    title = textFieldStateOf(),
                    details =
                        ActivityDetailsFields(
                            textFieldStateOf("Easy spin along the river, legs still heavy from Saturday."),
                            diveSite = null,
                            media =
                                persistentListOf(
                                    EditMedia.Existing("bafysaved", MediaItem.Photo("https://cdn.test/photo/1.jpg", 1.5f, null)),
                                    EditMedia.Picked(FormSamples.pickedPhoto),
                                ),
                            maxMedia = 4,
                            eventSink = {},
                        ),
                    gear = FormSamples.gear,
                    visibility = FormSamples.visibility,
                    leaderboardExclusion = false,
                )

    /** A dive, whose form names its site, holding all the photos it can. */
    private val dive: EditActivityState
        get() =
            state(ContentStates.loaded(Unit))
                .copy(
                    type = ActivityType(Sport.DIVE),
                    title = textFieldStateOf("Shark Reef"),
                    details =
                        ActivityDetailsFields(
                            textFieldStateOf(),
                            diveSite = textFieldStateOf("Ras Mohammed"),
                            media = persistentListOf(EditMedia.Picked(FormSamples.pickedPhoto), EditMedia.Picked(FormSamples.pickedPhoto)),
                            maxMedia = 2,
                            eventSink = {},
                        ),
                )

    /** The states beyond loading the native form draws: each question it asks, a save running and one failing. */
    val variants: Map<String, EditActivityState>
        get() =
            mapOf(
                "full" to full,
                "dive" to dive,
                "saving" to full.copy(saving = true),
                "saveFailed" to full.copy(error = UiError(UiError.Kind.SaveActivity)),
                "confirmDiscard" to full.copy(confirmDiscard = true),
                "confirmNarrowing" to full.copy(confirmNarrowing = true),
                "confirmCoreCharts" to full.copy(confirmCoreCharts = Visibility.FOLLOWERS),
            )
}
