package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.activityType
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * What the combine picker says (feed/combined-activities, dev/shared-display-text): its title, the explanation, the empty message, and each
 * candidate's lines. The Compose picker and the native one ask these.
 */
object CombinePickerTexts {
    val title: AppText = AppText.named("combine_title")
    val explained: AppText = AppText.named("combine_explained")
    val none: AppText = AppText.named("combine_none")

    /** A candidate's title, else its type's name. */
    fun heading(activity: ActivityView): AppText =
        activity.record.title?.let(AppText::written) ?: LabelText.activityType(activity.record.activityType)

    /** "Today at 7:05 AM · 1:02:03": when it started, on its own clock, and how long it took. */
    fun detail(activity: ActivityView, now: Instant, zone: TimeZone): AppText {
        val record = activity.record
        val start = FigureValue.StartTime(record.startedAt.epochSeconds, record.utcOffset, dateOnly = false)
        return AppText.named(
            "combine_row",
            FigureTexts.value(start, UnitSystem.Metric, now, zone),
            AppText.written(StatText.duration(record.elapsedTime)),
        )
    }

    /** Its type, for its icon. */
    fun type(activity: ActivityView): ActivityType = activity.record.activityType

    /** The device it was recorded on, when it named one. */
    fun device(activity: ActivityView): AppText? = activity.record.device?.let(AppText::written)

    /** [activity] is the one being combined, and shows a spinner. */
    fun isCombining(state: CombinePickerState, activity: ActivityView): Boolean = state.combining == activity.uri

    /** Candidates can be picked: none is being combined. */
    fun picks(state: CombinePickerState): Boolean = state.combining == null

    /** What picking [activity] sends. */
    fun pick(activity: ActivityView): CombinePickerEvent = CombinePickerEvent.Pick(activity.uri)
}
