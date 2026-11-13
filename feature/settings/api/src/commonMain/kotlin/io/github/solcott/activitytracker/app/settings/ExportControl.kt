package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.text.AppText

/** What a data export's button does; each UI sends its own page's event for it, and Save hands the archive to its platform. */
enum class ExportAction {
    /** Starts an export, or a stopped one again. */
    Export,

    /** Cancels the running export, or deletes the finished archive. */
    Cancel,

    /** Saves the finished archive. */
    Save,
}

/**
 * One of a data export's buttons for its status (auth/data-export, dev/shared-display-text), in the order shown: what it does, its words,
 * whether it is the step's primary button, and whether it can be used now.
 */
data class ExportControl(val action: ExportAction, val label: AppText, val primary: Boolean, val enabled: Boolean = true)
