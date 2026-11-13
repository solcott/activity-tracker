@file:Suppress("MatchingDeclarationName") // rememberArchiveHandOff; ArchiveHandOffResult is only what it reports.

package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.data.export.ExportArchive

/** How handing a data export's archive to the platform ended. */
enum class ArchiveHandOffResult {
    /** Written where the athlete chose: the app's copy can go. */
    Saved,

    /** The athlete dismissed the dialog: nothing was written, and the export stays to save later. */
    Dismissed,

    /** Writing it failed (no room, the destination went away). */
    Failed,
}

/**
 * Hands a finished data export to the platform (auth/data-export), zipping it straight into its destination rather than in memory: a save
 * dialog on Android and desktop, a download on the web (iOS's native Account view shares it itself). The returned function starts that and
 * returns at once; [onDone] hears how it ended.
 */
@Composable expect fun rememberArchiveHandOff(onDone: (ArchiveHandOffResult) -> Unit): (ExportArchive) -> Unit
