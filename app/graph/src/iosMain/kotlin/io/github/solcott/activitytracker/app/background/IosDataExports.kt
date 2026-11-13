package io.github.solcott.activitytracker.app.background

import io.github.solcott.activitytracker.activityfile.FitDecoder
import io.github.solcott.activitytracker.app.appGraph
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.account.AccountJobLauncher
import io.github.solcott.activitytracker.data.db.DataExportEntity
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.MainScope
import platform.BackgroundTasks.BGProcessingTask
import platform.Foundation.NSBundle
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
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
        registerProcessingTask(IDENTIFIER) { run(it, fitDecoder) }
        UNUserNotificationCenter.currentNotificationCenter().delegate = taps
        val graph = appGraph(fitDecoder)
        graph.dataExports.launcher = AccountJobLauncher { did, inProcess ->
            schedule()
            graph.dataExports.runInProcess(did) {
                inBackgroundTask("data-export") { inProcess() }
                if (graph.dataExports.current(did)?.state == DataExportEntity.STATE_READY) notifyReady()
            }
        }
    }

    /** Asks iOS for a run, in case the app is stopped before the export ends; it replaces any run already asked for. */
    fun schedule() = submitProcessingTask(IDENTIFIER, needsNetwork = true, what = "Background export")

    private fun run(task: BGProcessingTask, fitDecoder: FitDecoder) =
        task.runIn(scope, reschedule = ::schedule) {
            // Everything staged so far stays staged; what's left resumes on the next run.
            val graph = appGraph(fitDecoder)
            var ready = false
            val left = runSuspendCatching { graph.runDataExport { ready = it?.state == DataExportEntity.STATE_READY } }.getOrElse { true }
            if (ready) notifyReady()
            if (left == true) schedule()
            left != true
        }

    /** Says the export is ready, unless the app is in front, where the Account page already says so. */
    private suspend fun notifyReady() {
        if (UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive) return
        val title = localized("export_data_ready_notification_title")
        val body = localized("export_data_ready_notification_text")
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

/** The app's String Catalog entry [key], which `generateIosResources` makes from the shared `strings.xml` (dev/ios-native-resources). */
private fun localized(key: String): String = NSBundle.mainBundle.localizedStringForKey(key, value = null, table = null)
