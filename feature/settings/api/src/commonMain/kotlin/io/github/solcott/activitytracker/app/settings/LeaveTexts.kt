package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.auth.LeaveStep
import io.github.solcott.activitytracker.data.units.formatWhole
import kotlinx.collections.immutable.persistentListOf

/**
 * What Leave Activity Tracker says that follows a rule (auth/leaving), chosen here for the shared and the native UI
 * (dev/shared-display-text).
 */
object LeaveTexts {
    /** What leaving deletes, a bullet each. */
    fun deletedLines(): List<AppText> =
        listOf(
                "leave_deleted_activities",
                "leave_deleted_profile",
                "leave_deleted_gear",
                "leave_deleted_follows",
                "leave_deleted_interactions",
                "leave_deleted_segments",
                "leave_deleted_settings",
            )
            .map(AppText::named)

    /** What stays after leaving, a bullet each: the account at [handle], and other apps' data. */
    fun staysLines(handle: String): List<AppText> =
        listOf(AppText.named("leave_stays_account", AppText.written(handle)), AppText.named("leave_stays_other_apps"))

    /**
     * The shared places section's lines: that they're being counted, that they couldn't be (under the failure, which each UI draws as an
     * error), that there are none, or each count that isn't zero: those that stay first, then those deleted.
     */
    fun places(places: SharedPlacesSummary): List<AppText> =
        when (places) {
            SharedPlacesSummary.Loading -> listOf(AppText.named("leave_places_loading"))
            is SharedPlacesSummary.Failed -> listOf(AppText.named("leave_places_unknown"))
            is SharedPlacesSummary.Loaded -> {
                val counts =
                    listOfNotNull(
                        count("leave_places_active_segments", places.activeSegments),
                        count("leave_places_active_sites", places.activeSites),
                        count("leave_places_other_segments", places.otherSegments),
                        count("leave_places_other_sites", places.otherSites),
                    )
                counts.ifEmpty { listOf(AppText.named("leave_places_none")) }
            }
        }

    /** What this device would lose: changes not uploaded, an import saving, a recording. Empty when it holds none of them. */
    fun deviceLines(state: LeaveState): List<AppText> =
        listOfNotNull(
            count("leave_device_changes", state.pending?.changes ?: 0),
            if (state.pending?.importSaving == true) AppText.named("leave_device_import") else null,
            if (state.recording) AppText.named("leave_device_recording") else null,
        )

    /** The running leave's step, or that it's starting when it hasn't reported one. */
    fun step(run: LeaveRun): AppText =
        AppText.named(
            when (run.step) {
                null -> "leave_step_starting"
                LeaveStep.Discard -> "leave_step_discard"
                LeaveStep.Spaces -> "leave_step_spaces"
                LeaveStep.Repo -> "leave_step_repo"
                LeaveStep.Local -> "leave_step_local"
            },
        )

    /** How many records it has deleted, once it has deleted any. */
    fun recordsDeleted(run: LeaveRun): AppText? = count("leave_records_deleted", run.recordsDeleted)

    /** Until when the server asked it to wait, as a time of day on the device's clock, while it does. */
    fun paused(run: LeaveRun): AppText? = run.pausedUntil?.let { AppText.named("leave_paused", AppText.Time(it.epochSeconds)) }

    /** The line under a running leave: keep the app open while it runs, or that it stopped (with Retry beside it) once it failed. */
    fun runNote(run: LeaveRun): AppText = AppText.named(if (run.error == null) "leave_keep_open" else "leave_stopped")

    /** What the export section says: offering one, that one is running, or that the finished one is still to be saved. */
    fun exportLine(export: LeaveExport): AppText =
        AppText.named(
            when (export) {
                LeaveExport.None -> "leave_export_none"
                LeaveExport.Running -> "leave_export_running"
                LeaveExport.Ready -> "leave_export_ready"
            },
        )

    /** The export section's button: Export data (held while leaving starts), Cancel while one runs, or Save once it's ready. */
    fun exportControls(state: LeaveState): List<ExportControl> =
        listOf(
            when (state.export) {
                LeaveExport.None ->
                    ExportControl(ExportAction.Export, AppText.named("export_data"), primary = false, enabled = !state.starting)
                LeaveExport.Running -> ExportControl(ExportAction.Cancel, AppText.named("export_data_cancel"), primary = false)
                LeaveExport.Ready -> ExportControl(ExportAction.Save, AppText.named("leave_export_save"), primary = true)
            },
        )

    private fun count(name: String, quantity: Int): AppText? =
        if (quantity > 0) AppText.Plural(name, quantity, persistentListOf(AppText.written(formatWhole(quantity)))) else null
}
