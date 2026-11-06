package io.github.solcott.activitytracker.app.background

import io.github.solcott.activitytracker.activityfile.FitDecoder
import io.github.solcott.activitytracker.app.appGraph
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.account.AccountJobLauncher
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGProcessingTask
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid

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
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(IDENTIFIER, usingQueue = null) { task ->
            run(task as BGProcessingTask, fitDecoder)
        }
        val saves = appGraph(fitDecoder).importSaves
        saves.launcher = AccountJobLauncher { did, inProcess ->
            schedule()
            saves.runInProcess(did) { inBackgroundTask { inProcess() } }
        }
    }

    /** Asks iOS for a run, in case the app is stopped before a save ends; it replaces any run already asked for. */
    fun schedule() {
        val request = BGProcessingTaskRequest(IDENTIFIER)
        request.requiresNetworkConnectivity = false
        request.requiresExternalPower = false
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            // iOS refuses when Background App Refresh is off, too many requests are pending, or (the simulator) always; the save then
            // resumes when the app opens, so say why where Console shows it.
            if (!BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error.ptr)) {
                // A Kotlin String reaches a C variadic as a C string, so %@ needs an NSString.
                NSLog("Background saving not scheduled: %@", (error.value?.localizedDescription ?: "unknown error") as NSString)
            }
        }
    }

    /** Runs [block] inside a UIKit background task, so leaving the app gives it the time iOS allows before suspending it. */
    private suspend fun inBackgroundTask(block: suspend () -> Unit) {
        val application = UIApplication.sharedApplication
        var id = UIBackgroundTaskInvalid
        id =
            application.beginBackgroundTaskWithName("import-save") {
                // Out of time: the save stops where it is and resumes later.
                application.endBackgroundTask(id)
                id = UIBackgroundTaskInvalid
            }
        try {
            block()
        } finally {
            if (id != UIBackgroundTaskInvalid) application.endBackgroundTask(id)
        }
    }

    private fun run(task: BGProcessingTask, fitDecoder: FitDecoder) {
        val job = scope.launch {
            val left = runSuspendCatching { appGraph(fitDecoder).runImportSaves() }.getOrElse { true }
            if (left == true) schedule()
            task.setTaskCompletedWithSuccess(left != true)
        }
        task.expirationHandler = {
            // Out of time: every activity saved so far stays saved; ask again for the rest.
            job.cancel()
            schedule()
            task.setTaskCompletedWithSuccess(false)
        }
    }
}
