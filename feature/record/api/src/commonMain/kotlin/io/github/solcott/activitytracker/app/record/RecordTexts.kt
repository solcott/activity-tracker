package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabeledText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.location.RecordingState

/** A control the recording screen offers, by what it does. */
enum class RecordAction {
    Start,
    Pause,
    Resume,
    Finish,
    Discard,
}

/**
 * What the recording screen says and offers (record/gps-recording, dev/shared-display-text): its figures in the athlete's units, its
 * notices, and the controls for the recording's status. The shared `RecordUi` and the native iOS screen ask them.
 */
object RecordTexts {
    val title: AppText = AppText.named("record_title")

    /** An unfinished recording offered back after the app stopped. */
    val recovered: AppText = AppText.named("record_recovered")

    /** The recording's last change couldn't be saved on the device; a failure. */
    val saveFailed: AppText = AppText.named("record_save_failed")

    /** Location access was refused, so Start can't record until it's allowed in Settings. */
    val locationOff: AppText = AppText.named("record_location_off")

    val openSettings: AppText = AppText.named("record_open_settings")

    /** Asked before Discard throws the recording away. */
    val discardConfirm: AppText = AppText.named("record_discard_confirm")

    /** The time recorded, pauses left out: "42:00", "1:05:12". */
    fun elapsed(state: RecordState): AppText = AppText.written(StatText.duration(state.elapsed.inWholeSeconds))

    /** The figures under the time: the distance, the sport's average rate when it has one, and the climb. */
    fun stats(state: RecordState, units: UnitSystem): List<LabeledText> =
        listOfNotNull(
            LabeledText(AppText.named("stat_distance"), StatText.distance(state.distanceMeters, units)),
            StatText.rate(state.type.selected.sport, state.distanceMeters, state.movingSeconds, units),
            LabeledText(AppText.named("stat_elevation"), StatText.elevation(state.elevationGainMeters, units)),
        )

    /** The controls for the recording's status, in order. Every one is held while the recording is being saved ([RecordState.saving]). */
    fun actions(state: RecordState): List<RecordAction> =
        when (state.status) {
            RecordingState.Status.Idle -> listOf(RecordAction.Start)
            RecordingState.Status.Recording -> listOf(RecordAction.Pause)
            RecordingState.Status.Paused -> listOf(RecordAction.Resume, RecordAction.Finish, RecordAction.Discard)
        }

    fun label(action: RecordAction): AppText =
        AppText.named(
            when (action) {
                RecordAction.Start -> "record_start"
                RecordAction.Pause -> "record_pause"
                RecordAction.Resume -> "record_resume"
                RecordAction.Finish -> "record_finish"
                RecordAction.Discard -> "record_discard"
            },
        )

    /** The type is chosen before recording starts: the recorder's samples are measured for it. */
    fun typeChangeable(state: RecordState): Boolean = state.status == RecordingState.Status.Idle && !state.saving
}
