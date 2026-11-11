package io.github.solcott.activitytracker.app.uploads

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DayText
import io.github.solcott.activitytracker.app.text.DurationText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.activity.ImportSaveStatus
import io.github.solcott.activitytracker.data.activity.RejectedChange
import io.github.solcott.activitytracker.data.activity.UploadImport
import io.github.solcott.activitytracker.data.activity.UploadItem
import io.github.solcott.activitytracker.data.activity.UploadSource
import io.github.solcott.activitytracker.data.activity.UploadState
import io.github.solcott.activitytracker.data.units.formatWhole
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * What the upload queue says of its items, pauses and questions (data/upload-queue), chosen here for the shared and the native UI
 * (dev/shared-display-text). A day in a name or a pause is read in the [TimeZone] and beside the current [Instant] each UI passes.
 */
object UploadsTexts {
    /** "Waiting", "Uploading, 2 of 5", "Uploaded", "Refused by the server" or why it was skipped. */
    fun state(item: UploadItem): AppText =
        when (item.state) {
            UploadState.Waiting -> AppText.named("uploads_state_waiting")
            UploadState.Uploading -> AppText.named("uploads_state_uploading", whole(item.sent), whole(item.total))
            UploadState.Uploaded -> AppText.named("uploads_state_uploaded")
            UploadState.Refused -> AppText.named("uploads_state_refused")
            UploadState.Skipped -> AppText.named("uploads_state_skipped")
        }

    /** An item by its title, or its sport and its day in [zone]; an edit says it updates the activity ("Updating Lunch Swim"). */
    fun itemName(item: UploadItem, zone: TimeZone): AppText {
        val name = activityName(item.title, item.sport, item.startedAt, zone)
        return if (item.isEdit) AppText.named("uploads_updating", name) else name
    }

    /** A refused change: an activity by its title, or its sport and day in [zone]; anything else by its kind ("A like"). */
    fun changeName(change: RejectedChange, zone: TimeZone): AppText =
        when (change.kind) {
            RejectedChange.Kind.Activity -> activityName(change.title, change.sport, change.startedAt, zone)
            RejectedChange.Kind.Like -> AppText.named("unsent_change_like")
            RejectedChange.Kind.Repost -> AppText.named("unsent_change_repost")
            RejectedChange.Kind.Comment -> AppText.named("unsent_change_comment")
            RejectedChange.Kind.CommentHide -> AppText.named("unsent_change_comment_hide")
            RejectedChange.Kind.Block -> AppText.named("unsent_change_block")
            RejectedChange.Kind.Mute -> AppText.named("unsent_change_mute")
            RejectedChange.Kind.Segment -> AppText.named("unsent_change_segment")
            RejectedChange.Kind.SegmentStar -> AppText.named("unsent_change_segment_star")
            RejectedChange.Kind.Other -> AppText.named("unsent_change_other")
        }

    /** Cancel read aloud with what it cancels: "Cancel Morning Ride". */
    fun cancel(name: AppText): AppText = AppText.named("uploads_cancel_named", name)

    /** Send again read aloud with what it sends: "Send A like again". */
    fun sendAgain(name: AppText): AppText = AppText.named("uploads_send_again_named", name)

    /**
     * The server's pause, while one is shown: "Uploads paused by the server until 3:40 PM", or "until tomorrow at" when it ends after
     * [now]'s day in [zone]; null with none.
     */
    fun serverPaused(state: UploadsState, now: Instant, zone: TimeZone): AppText? =
        state.serverPausedUntil?.let { until ->
            DayText.until(until, now, zone, today = "uploads_server_paused", tomorrow = "uploads_server_paused_tomorrow")
        }

    /** "About 40 min left", "About 3 h left" or "About 2 days left" (the estimate is rough); null with no estimate. */
    fun timeLeft(state: UploadsState): AppText? = state.timeLeft?.let { AppText.named("uploads_time_left", DurationText.approximate(it)) }

    /** What cancelling the rest of the import being confirmed drops: also its activities not saved yet, while it's still saving. */
    fun cancelRestMessage(state: UploadsState): AppText =
        AppText.named(if (state.cancelRestSaving) "uploads_cancel_rest_message_saving" else "uploads_cancel_rest_message")

    private fun activityName(title: String?, sport: String?, startedAt: Instant?, zone: TimeZone): AppText =
        when {
            title != null -> AppText.written(title)
            sport != null && startedAt != null ->
                AppText.named(
                    "unsent_change_activity_named",
                    LabelText.sport(sport),
                    AppText.written(startedAt.toLocalDateTime(zone).date.toString()),
                )
            else -> AppText.named("unsent_change_activity")
        }
}

/** What the upload queue says of an import's group (data/upload-queue), for the shared and the native UI (dev/shared-display-text). */
object UploadImportTexts {
    /** "Strava export", "Strava and Garmin exports", "Account export", or the files picked: "ride.fit", "ride.fit and 11 more". */
    fun source(source: UploadSource): AppText =
        when (source) {
            is UploadSource.Export ->
                when (source.kinds.size) {
                    0 -> AppText.named("uploads_source_exports_unknown")
                    1 -> AppText.named("uploads_source_export", AppText.written(source.kinds.single()))
                    else -> AppText.named("uploads_source_exports", list(source.kinds))
                }
            is UploadSource.Files -> {
                val more = source.count - 1
                if (more <= 0) {
                    AppText.written(source.first)
                } else {
                    AppText.Plural("uploads_source_files", more, persistentListOf(AppText.written(source.first), whole(more)))
                }
            }
        }

    /** When it was saved, beside [now]'s day in [zone]: "Saved Today at 7:05 AM". */
    fun saved(group: UploadImport, now: Instant, zone: TimeZone): AppText =
        AppText.named("uploads_import_saved", DayText.dayAndTime(group.savedAt, now, zone))

    /** "Saving 500 of 1,362" while its save runs, or that it stopped short (an error); null once saved. */
    fun saveStatus(group: UploadImport): AppText? =
        when (val save = group.save) {
            is ImportSaveStatus.Saving -> AppText.named("uploads_import_saving", whole(save.done), whole(save.total))
            is ImportSaveStatus.Stopped -> AppText.named("uploads_import_stopped", whole(save.saved), whole(save.total))
            null -> null
        }

    /** "412 of 1,362 uploaded". */
    fun progress(group: UploadImport): AppText = AppText.named("uploads_import_progress", whole(group.uploaded), whole(group.saved))

    /** "1 refused · 3 skipped", either alone, or null with neither. */
    fun counts(group: UploadImport): AppText? {
        val refused = count("uploads_import_refused", group.refused)
        val skipped = count("uploads_import_skipped", group.skipped)
        return when {
            refused != null && skipped != null -> AppText.named("uploads_import_counts", refused, skipped)
            else -> refused ?: skipped
        }
    }

    /** "Show all (26)": every one of its live activities. */
    fun showAll(group: UploadImport): AppText = AppText.named("uploads_show_all", whole(group.live.size))

    /** Cancel rest read aloud with the import's source: "Cancel the rest of Strava export". */
    fun cancelRest(group: UploadImport): AppText = AppText.named("uploads_cancel_rest_named", source(group.source))

    /** "A, B and C": every name but the last joined by commas, then "and" the last. */
    private fun list(names: List<String>): AppText =
        names
            .dropLast(1)
            .map(AppText::written)
            .reduce { joined, next -> AppText.named("uploads_list_pair", joined, next) }
            .let { head -> AppText.named("uploads_list_last", head, AppText.written(names.last())) }

    private fun count(name: String, quantity: Int): AppText? =
        if (quantity > 0) AppText.Plural(name, quantity, persistentListOf(whole(quantity))) else null
}

/** [count] with its digits grouped as the device's locale groups them ("1,362"). */
private fun whole(count: Int): AppText = AppText.written(formatWhole(count))
