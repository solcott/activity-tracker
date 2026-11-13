package io.github.solcott.activitytracker.app.background

import io.github.solcott.activitytracker.activityfile.FitDecoder
import io.github.solcott.activitytracker.app.appGraph
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.account.AccountJobLauncher
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.MainScope
import platform.BackgroundTasks.BGProcessingTask

/**
 * Batch saves go on while the app is in the background, and resume after iOS stops it (feed/batch-import): a save runs in the app's process
 * inside a background task, which buys the time iOS gives an app that just left the foreground, and a `BGProcessingTask` (no connection
 * needed) resumes what's left when iOS chooses, as does the next launch. Used by `iosApp/iosApp/iOSApp.swift`.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object IosImportSaves {
    /** Listed in `BGTaskSchedulerPermittedIdentifiers` in `iosApp/Info.plist`. */
    const val IDENTIFIER = "io.github.solcott.activitytracker.importsave"

    private val scope = MainScope()

    /** Registers the task's handler, as iOS requires before the app finishes launching, and runs every save in a background task. */
    fun register(fitDecoder: FitDecoder) {
        registerProcessingTask(IDENTIFIER) { run(it, fitDecoder) }
        val saves = appGraph(fitDecoder).importSaves
        saves.launcher = AccountJobLauncher { did, inProcess ->
            schedule()
            saves.runInProcess(did) { inBackgroundTask("import-save") { inProcess() } }
        }
    }

    /** Asks iOS for a run, in case the app is stopped before a save ends; it replaces any run already asked for. */
    fun schedule() = submitProcessingTask(IDENTIFIER, needsNetwork = false, what = "Background saving")

    private fun run(task: BGProcessingTask, fitDecoder: FitDecoder) =
        task.runIn(scope, reschedule = ::schedule) {
            // Every activity saved so far stays saved; what's left resumes on the next run.
            val left = runSuspendCatching { appGraph(fitDecoder).runImportSaves() }.getOrElse { true }
            if (left == true) schedule()
            left != true
        }
}
