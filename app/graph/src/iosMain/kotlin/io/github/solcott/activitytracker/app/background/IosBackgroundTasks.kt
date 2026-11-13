package io.github.solcott.activitytracker.app.background

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGProcessingTask
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid

// The `BGProcessingTask` and UIKit background-task plumbing that uploads, batch saves and data exports share.

/** Registers [handler] for the processing task [identifier], as iOS requires before the app finishes launching. */
internal fun registerProcessingTask(identifier: String, handler: (BGProcessingTask) -> Unit) {
    BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(identifier, usingQueue = null) { task ->
        handler(task as BGProcessingTask)
    }
}

/**
 * Asks iOS for a run of the processing task [identifier] ([needsNetwork] or not, never needing power, as [configure] adjusts), replacing
 * any run already asked for. iOS refuses when Background App Refresh is off, too many requests are pending, or (the simulator) always; the
 * work then waits for the app to open, so the log says why where Console shows it, naming [what].
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun submitProcessingTask(
    identifier: String,
    needsNetwork: Boolean,
    what: String,
    configure: BGProcessingTaskRequest.() -> Unit = {},
) {
    val request = BGProcessingTaskRequest(identifier)
    request.requiresNetworkConnectivity = needsNetwork
    request.requiresExternalPower = false
    request.configure()
    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        if (!BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error.ptr)) {
            // A Kotlin String reaches a C variadic as a C string, so %@ needs an NSString.
            NSLog("$what not scheduled: %@", (error.value?.localizedDescription ?: "unknown error") as NSString)
        }
    }
}

/**
 * Runs [work] for this task in [scope], completing it with whether [work] finished everything. When iOS ends the task's time first, the
 * work is cancelled where it is (what it did stays done), [reschedule] asks again for the rest, and the task completes as unfinished.
 */
internal fun BGProcessingTask.runIn(scope: CoroutineScope, reschedule: () -> Unit, work: suspend () -> Boolean) {
    val task = this
    val job = scope.launch { task.setTaskCompletedWithSuccess(work()) }
    task.expirationHandler = {
        job.cancel()
        reschedule()
        task.setTaskCompletedWithSuccess(false)
    }
}

/**
 * Runs [block] inside a UIKit background task named [name], so leaving the app gives it the time iOS allows before suspending it. Out of
 * time, the task ends and the work stops where it is, to resume later.
 */
internal suspend fun inBackgroundTask(name: String, block: suspend () -> Unit) {
    val application = UIApplication.sharedApplication
    var id = UIBackgroundTaskInvalid
    id =
        application.beginBackgroundTaskWithName(name) {
            application.endBackgroundTask(id)
            id = UIBackgroundTaskInvalid
        }
    try {
        block()
    } finally {
        if (id != UIBackgroundTaskInvalid) application.endBackgroundTask(id)
    }
}
