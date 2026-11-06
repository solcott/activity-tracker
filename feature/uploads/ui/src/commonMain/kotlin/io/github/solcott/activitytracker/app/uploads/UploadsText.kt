package io.github.solcott.activitytracker.app.uploads

import androidx.compose.foundation.layout.plus
import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.detail.untilText
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.sportLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_activity
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_activity_named
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_block
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_comment
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_comment_hide
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_like
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_mute
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_other
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_segment
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_segment_star
import io.github.solcott.activitytracker.composeApp.resources.uploads_list_last
import io.github.solcott.activitytracker.composeApp.resources.uploads_list_separator
import io.github.solcott.activitytracker.composeApp.resources.uploads_server_paused
import io.github.solcott.activitytracker.composeApp.resources.uploads_server_paused_tomorrow
import io.github.solcott.activitytracker.composeApp.resources.uploads_source_export
import io.github.solcott.activitytracker.composeApp.resources.uploads_source_exports
import io.github.solcott.activitytracker.composeApp.resources.uploads_source_exports_unknown
import io.github.solcott.activitytracker.composeApp.resources.uploads_source_files
import io.github.solcott.activitytracker.composeApp.resources.uploads_state_refused
import io.github.solcott.activitytracker.composeApp.resources.uploads_state_skipped
import io.github.solcott.activitytracker.composeApp.resources.uploads_state_uploaded
import io.github.solcott.activitytracker.composeApp.resources.uploads_state_uploading
import io.github.solcott.activitytracker.composeApp.resources.uploads_state_waiting
import io.github.solcott.activitytracker.composeApp.resources.uploads_time_left_days
import io.github.solcott.activitytracker.composeApp.resources.uploads_time_left_hours
import io.github.solcott.activitytracker.composeApp.resources.uploads_time_left_minutes
import io.github.solcott.activitytracker.composeApp.resources.uploads_updating
import io.github.solcott.activitytracker.data.activity.RejectedChange
import io.github.solcott.activitytracker.data.activity.UploadItem
import io.github.solcott.activitytracker.data.activity.UploadSource
import io.github.solcott.activitytracker.data.activity.UploadState
import io.github.solcott.activitytracker.data.units.formatWhole
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun stateText(item: UploadItem): String =
    when (item.state) {
        UploadState.Waiting -> stringResource(Res.string.uploads_state_waiting)
        UploadState.Uploading -> stringResource(Res.string.uploads_state_uploading, grouped(item.sent), grouped(item.total))
        UploadState.Uploaded -> stringResource(Res.string.uploads_state_uploaded)
        UploadState.Refused -> stringResource(Res.string.uploads_state_refused)
        UploadState.Skipped -> stringResource(Res.string.uploads_state_skipped)
    }

/** An item by its title, or its sport and day; an edit says it updates the activity. */
@Composable
internal fun itemName(item: UploadItem): String {
    val name = activityName(item.title, item.sport, item.startedAt)
    return if (item.isEdit) stringResource(Res.string.uploads_updating, name) else name
}

@Composable
private fun activityName(title: String?, sport: String?, startedAt: Instant?): String =
    when {
        title != null -> title
        sport != null && startedAt != null ->
            stringResource(
                Res.string.unsent_change_activity_named,
                sportLabel(sport),
                startedAt.toLocalDateTime(LocalTimeZone.current).date.toString(),
            )
        else -> stringResource(Res.string.unsent_change_activity)
    }

/** An activity by its title, or its sport and day; anything else by its kind. */
@Composable
internal fun changeName(change: RejectedChange): String =
    when (change.kind) {
        RejectedChange.Kind.Activity -> activityName(change.title, change.sport, change.startedAt)
        RejectedChange.Kind.Like -> stringResource(Res.string.unsent_change_like)
        RejectedChange.Kind.Comment -> stringResource(Res.string.unsent_change_comment)
        RejectedChange.Kind.CommentHide -> stringResource(Res.string.unsent_change_comment_hide)
        RejectedChange.Kind.Block -> stringResource(Res.string.unsent_change_block)
        RejectedChange.Kind.Mute -> stringResource(Res.string.unsent_change_mute)
        RejectedChange.Kind.Segment -> stringResource(Res.string.unsent_change_segment)
        RejectedChange.Kind.SegmentStar -> stringResource(Res.string.unsent_change_segment_star)
        RejectedChange.Kind.Other -> stringResource(Res.string.unsent_change_other)
    }

/** "Uploads paused by the server until 3:40 PM", or "until tomorrow at" when the pause ends after midnight. */
@Composable
internal fun serverPausedUntil(until: Instant): String =
    untilText(until, Res.string.uploads_server_paused, Res.string.uploads_server_paused_tomorrow)

/** "About 40 min left", "About 3 h left" or "About 2 days left": the estimate is rough, so it's rounded to its largest unit. */
@Composable
internal fun timeLeft(left: Duration): String =
    when {
        left < 1.hours -> {
            val minutes = left.inWholeMinutes.toInt().coerceAtLeast(1)
            pluralStringResource(Res.plurals.uploads_time_left_minutes, minutes, minutes)
        }
        left < 1.days -> {
            val hours = (left / 1.hours).roundToInt().coerceAtLeast(1)
            pluralStringResource(Res.plurals.uploads_time_left_hours, hours, hours)
        }
        else -> {
            val days = (left / 1.days).roundToInt().coerceAtLeast(1)
            pluralStringResource(Res.plurals.uploads_time_left_days, days, days)
        }
    }

/** [count] with its digits grouped by thousands, as the device's locale groups them ("1,362"). */
internal fun grouped(count: Int): String = formatWhole(count)

/** "Strava export", "Strava and Garmin exports", or the files picked: "ride.fit", "ride.fit and 11 more". */
@Composable
internal fun sourceName(source: UploadSource): String =
    when (source) {
        is UploadSource.Export ->
            when (source.kinds.size) {
                0 -> stringResource(Res.string.uploads_source_exports_unknown)
                1 -> stringResource(Res.string.uploads_source_export, source.kinds.single())
                else -> {
                    val separator = stringResource(Res.string.uploads_list_separator)
                    val names =
                        stringResource(Res.string.uploads_list_last, source.kinds.dropLast(1).joinToString(separator), source.kinds.last())
                    stringResource(Res.string.uploads_source_exports, names)
                }
            }
        is UploadSource.Files -> {
            val more = source.count - 1
            if (more <= 0) source.first else pluralStringResource(Res.plurals.uploads_source_files, more, source.first, more)
        }
    }
