package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.activity.CombineCue
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.MemberView
import io.github.solcott.activitytracker.lexicon.app.RecordingView
import io.github.solcott.activitytracker.lexicon.app.activityType
import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone

/**
 * How an activity's other recordings, a combined activity's members and a combine suggestion's cue read (feed/combined-activities,
 * feed/combine-suggestions, dev/shared-display-text). The shared `Recordings`, `Members` and `combineCueLabel` and the native page ask
 * them.
 */
object RecordingTexts {
    val recordingOf: AppText = AppText.named("detail_recording_of")
    val openRecorded: AppText = AppText.named("detail_open_recorded")
    val recordings: AppText = AppText.named("detail_recordings")
    val memberOf: AppText = AppText.named("detail_member_of")
    val openCombined: AppText = AppText.named("detail_open_combined")
    val members: AppText = AppText.named("detail_members")

    /** The recording's device; or, for a file that named none, when it started next to [activityStart], to tell such recordings apart. */
    fun recordingName(recording: RecordingView, activityStart: Instant): AppText {
        recording.device?.let {
            return AppText.written(it)
        }
        val minutes = (recording.startedAt - activityStart).inWholeMinutes.toInt()
        return when {
            minutes > 0 -> AppText.Plural("detail_recording_unnamed_later", minutes, persistentListOf(AppText.written("$minutes")))
            minutes < 0 ->
                AppText.Plural("detail_recording_unnamed_earlier", abs(minutes), persistentListOf(AppText.written("${abs(minutes)}")))
            else -> AppText.named("detail_recording_unnamed")
        }
    }

    /**
     * A member's device; or, for a file that named none, its sport. Not its start: a member whose start is hidden is served at its local
     * day's midnight, so it would read as hours away from the combined activity.
     */
    fun memberName(member: MemberView): AppText =
        member.device?.let(AppText::written) ?: member.sport?.let(LabelText::sport) ?: AppText.named("detail_recording_unnamed")

    /**
     * A card's caption on a combined activity: the devices its data came from, in time order ("From Edge 840", "From Forerunner, then
     * Phone"), each by [memberName]; null for none.
     */
    fun sourceCaption(members: List<MemberView>): AppText? =
        members
            .map(::memberName)
            .reduceOrNull { acc, name -> AppText.named("chart_source_then", acc, name) }
            ?.let { AppText.named("chart_source_from", it) }

    /** What a combine suggestion looks like, as a question. */
    fun combineCue(cue: CombineCue): AppText =
        AppText.named(
            when (cue) {
                CombineCue.SameActivity -> "combine_cue_same_activity"
                CombineCue.SwitchedDevices -> "combine_cue_switched_devices"
                CombineCue.ShuttleOrLift -> "combine_cue_shuttle"
                CombineCue.Break -> "combine_cue_break"
            },
        )

    /**
     * [activity] as a combine suggestion names it: its title (or type), then when it started on its own clock and how long it lasted
     * ("Lunch loop (Today at 12:01 PM · 52:00)").
     */
    fun suggestedActivity(activity: ActivityView, now: Instant, zone: TimeZone): AppText {
        val record = activity.record
        val start = FigureValue.StartTime(record.startedAt.epochSeconds, record.utcOffset, dateOnly = false)
        return AppText.named(
            "combine_suggested_activity",
            record.title?.let(AppText::written) ?: LabelText.activityType(record.activityType),
            AppText.named(
                "combine_row",
                FigureTexts.value(start, UnitSystem.Metric, now, zone),
                AppText.written(StatText.duration(record.elapsedTime)),
            ),
        )
    }
}
