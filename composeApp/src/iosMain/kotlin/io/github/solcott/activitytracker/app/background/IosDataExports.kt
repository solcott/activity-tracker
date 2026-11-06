package io.github.solcott.activitytracker.app.background

import io.github.solcott.activitytracker.activityfile.FitDecoder
import io.github.solcott.activitytracker.app.appGraph
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.export_data_ready_notification_text
import io.github.solcott.activitytracker.composeApp.resources.export_data_ready_notification_title
import io.github.solcott.activitytracker.data.db.DataExportEntity
import io.github.solcott.activitytracker.data.export.DataExportLauncher
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import platform.BackgroundTasks.BGProcessingTask
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * Data exports go on while the app is in the background, and resume after iOS stops it (auth/data-export): an export runs in the app's
 * process inside a background task, which buys the time iOS gives an app that just left the foreground, and a `BGProcessingTask` (needing a
 * connection) resumes it when iOS chooses, as does the next launch. When an export becomes ready with the app in the background, a local
 * notification says so (delivered quietly, so the athlete isn't asked for permission first); tapping it opens the Account page. Used by
 * `iosApp/iosApp/iOSApp.swift`.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object IosDataExports {
    /** Listed in `BGTaskSchedulerPermittedIdentifiers` in `iosApp/Info.plist`. */
    const val IDENTIFIER = "io.github.solcott.activitytracker.dataexport"
    private const val READY = "data-export-ready"

    private val scope = MainScope()
    private var decoder: FitDecoder? = null

    /** Registers the task's handler, as iOS requires before the app finishes launching, and runs every export in a background task. */
    fun register(fitDecoder: FitDecoder) {
        decoder = fitDecoder
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(IDENTIFIER, usingQueue = null) { task ->
            run(task as BGProcessingTask, fitDecoder)
        }
        UNUserNotificationCenter.currentNotificationCenter().delegate = taps
        val graph = appGraph(fitDecoder)
        graph.dataExports.launcher = DataExportLauncher { did, inProcess ->
            schedule()
            graph.dataExports.runInProcess(did) {
                inBackgroundTask { inProcess() }
                if (graph.dataExports.current(did)?.state == DataExportEntity.STATE_READY) notifyReady()
            }
        }
    }

    /** Asks iOS for a run, in case the app is stopped before the export ends; it replaces any run already asked for. */
    fun schedule() {
        val request = BGProcessingTaskRequest(IDENTIFIER)
        request.requiresNetworkConnectivity = true
        request.requiresExternalPower = false
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            // iOS refuses when Background App Refresh is off, too many requests are pending, or (the simulator) always; the export then
            // resumes when the app opens, so say why where Console shows it.
            if (!BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error.ptr)) {
                NSLog("Background export not scheduled: %@", (error.value?.localizedDescription ?: "unknown error") as NSString)
            }
        }
    }

    /** Runs [block] inside a UIKit background task, so leaving the app gives it the time iOS allows before suspending it. */
    private suspend fun inBackgroundTask(block: suspend () -> Unit) {
        val application = UIApplication.sharedApplication
        var id = UIBackgroundTaskInvalid
        id =
            application.beginBackgroundTaskWithName("data-export") {
                // Out of time: the export stops where it is and resumes later.
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
            val graph = appGraph(fitDecoder)
            var ready = false
            val left = runSuspendCatching { graph.runDataExport { ready = it?.state == DataExportEntity.STATE_READY } }.getOrElse { true }
            if (ready) notifyReady()
            if (left == true) schedule()
            task.setTaskCompletedWithSuccess(left != true)
        }
        task.expirationHandler = {
            // Out of time: everything staged so far stays staged; ask again for the rest.
            job.cancel()
            schedule()
            task.setTaskCompletedWithSuccess(false)
        }
    }

    /** Says the export is ready, unless the app is in front, where the Account page already says so. */
    private suspend fun notifyReady() {
        if (UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive) return
        val title = getString(Res.string.export_data_ready_notification_title)
        val body = getString(Res.string.export_data_ready_notification_text)
        val center = UNUserNotificationCenter.currentNotificationCenter()
        // Provisional: delivered quietly to Notification Center without asking first; a choice the athlete made already stands.
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionProvisional) { _, _ ->
            val content = UNMutableNotificationContent()
            content.setTitle(title)
            content.setBody(body)
            center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(READY, content, null), null)
        }
    }

    /** Opens the Account page when the ready notification is tapped, and shows it as a banner if it arrives with the app in front. */
    // A class, not an object: Kotlin/Native can't make an object of an Objective-C class. Held here because the delegate is weak.
    private val taps = Taps()

    private class Taps : NSObject(), UNUserNotificationCenterDelegateProtocol {
        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            didReceiveNotificationResponse: UNNotificationResponse,
            withCompletionHandler: () -> Unit,
        ) {
            if (didReceiveNotificationResponse.notification.request.identifier == READY) {
                decoder?.let { appGraph(it).accountScreenRequests.request() }
            }
            withCompletionHandler()
        }

        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            willPresentNotification: UNNotification,
            withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
        ) {
            withCompletionHandler(UNNotificationPresentationOptionBanner)
        }
    }
}
