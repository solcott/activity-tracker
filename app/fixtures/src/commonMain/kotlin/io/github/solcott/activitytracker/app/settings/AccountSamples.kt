package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.data.auth.GrantFeature
import io.github.solcott.activitytracker.data.auth.GrantStatus
import io.github.solcott.activitytracker.data.auth.PendingWork
import io.github.solcott.activitytracker.data.export.ArchiveZipper
import io.github.solcott.activitytracker.data.export.ExportArchive
import io.github.solcott.activitytracker.data.export.ExportFiles
import kotlinx.datetime.LocalDateTime

/** No staged files: a sample's archive is only ever shown, never written. */
private object NoExportFiles : ExportFiles {
    override suspend fun write(exportId: String, path: String, bytes: ByteArray) = Unit

    override suspend fun read(exportId: String, path: String): ByteArray? = null

    override suspend fun list(exportId: String): List<String> = emptyList()

    override suspend fun delete(exportId: String) = Unit

    override suspend fun exportIds(): List<String> = emptyList()

    override suspend fun freeBytes(): Long? = null
}

object AccountSamples {
    private val archive =
        ExportArchive(
            "3kexport",
            "activity-tracker-alice.test-2026-11-05.zip",
            LocalDateTime(2026, 11, 5, 12, 0),
            ArchiveZipper(NoExportFiles),
        )

    val default: AccountState
        get() =
            AccountState(
                handle = "alice.test",
                did = "did:plc:4z5n2d7xkqvu3hy6wbjmlcfe",
                pdsHost = "pds.example.com",
                grant = GrantStatus.Current,
                eventSink = {},
            )

    val all: Map<String, AccountState>
        get() =
            mapOf(
                SampleKind.LOADED to default,
                "outdated" to
                    default.copy(grant = GrantStatus.Outdated(setOf(GrantFeature.PrivateInteractions, GrantFeature.SegmentsAndGroups))),
                "noSpaceScope" to default.copy(grant = GrantStatus.Outdated(GrantFeature.all)),
                "spacesUnsupported" to default.copy(grant = GrantStatus.SpacesUnsupported),
                "pendingUploads" to
                    default.copy(confirming = EndingSession(signInAgain = false, PendingWork(changes = 3, importSaving = false))),
                "pendingImport" to
                    default.copy(confirming = EndingSession(signInAgain = false, PendingWork(changes = 12, importSaving = true))),
                "exportRunning" to
                    default.copy(
                        export =
                            DataExportStatus.Running(
                                records = 2412,
                                filesDone = 900,
                                filesTotal = 900,
                                activitiesDone = 120,
                                activitiesTotal = 400,
                            ),
                    ),
                "exportGathering" to default.copy(export = DataExportStatus.Running(1210, 0, 0, 0, 0)),
                "exportReady" to
                    default.copy(export = DataExportStatus.Ready(archive, problems = 0, unsentChanges = 0, spacesIncluded = true)),
                "exportReadyWithProblems" to
                    default.copy(export = DataExportStatus.Ready(archive, problems = 1, unsentChanges = 2, spacesIncluded = false)),
                "exportPaused" to default.copy(export = DataExportStatus.Paused(neededBytes = 1_200L * 1024 * 1024)),
                "exportFailed" to default.copy(export = DataExportStatus.Failed),
                "exportSharedAsk" to
                    default.copy(
                        export = DataExportStatus.Ready(archive, problems = 0, unsentChanges = 0, spacesIncluded = true),
                        askingKeptShared = true,
                    ),
                "pendingExport" to
                    default.copy(
                        export = DataExportStatus.Running(10, 0, 0, 0, 0),
                        confirming =
                            EndingSession(signInAgain = false, PendingWork(changes = 0, importSaving = false, exportPending = true)),
                    ),
                "signInAgainPending" to
                    default.copy(
                        grant = GrantStatus.Outdated(GrantFeature.all),
                        confirming = EndingSession(signInAgain = true, PendingWork(changes = 1, importSaving = false)),
                    ),
            )

    /**
     * States the native page is checked in beyond [all], kept apart so the shared screenshot set stays as it is: the session ending, with
     * every action off, and a save of the ready export that failed.
     */
    val variants: Map<String, AccountState>
        get() =
            mapOf(
                "ending" to default.copy(ending = true, grant = GrantStatus.Outdated(setOf(GrantFeature.Leaving))),
                "exportSaveFailed" to
                    default.copy(
                        export = DataExportStatus.Ready(archive, problems = 0, unsentChanges = 0, spacesIncluded = true),
                        exportError = UiError(UiError.Kind.SaveDataExport),
                    ),
            )
}
