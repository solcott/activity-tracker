package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.units.formatWhole
import kotlinx.collections.immutable.persistentListOf

/**
 * What the choice after leaving on another device says that follows a rule (auth/leaving), chosen here for the shared and the native UI
 * (dev/shared-display-text).
 */
object LeftElsewhereTexts {
    /** That the athlete left on another device, while this one still holds work for their handle. */
    fun intro(state: LeftElsewhereState): AppText = AppText.named("left_elsewhere_intro", AppText.written(state.handle))

    /** A line for each kind of work this device holds, only those it holds: changes not uploaded, an import, an export, a recording. */
    fun lines(state: LeftElsewhereState): List<AppText> =
        listOfNotNull(
            if (state.changes > 0) {
                AppText.Plural("left_elsewhere_changes", state.changes, persistentListOf(AppText.written(formatWhole(state.changes))))
            } else {
                null
            },
            if (state.importSaving) AppText.named("left_elsewhere_import") else null,
            if (state.exportPending) AppText.named("left_elsewhere_export") else null,
            if (state.recording) AppText.named("left_elsewhere_recording") else null,
        )
}
