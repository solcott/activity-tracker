package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.export.ExportArchive
import io.github.solcott.activitytracker.data.export.writeTo
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.path
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

// The share sheet needs a file: the zip streams into the cache dir under its name (replacing an earlier one), the sheet offers it, and
// it goes once the sheet closes.
// The sheet says only whether an activity ran, not whether the zip was kept, so after one runs the athlete is asked before the app's copy
// goes. That question waits for the sheet to close: UIKit won't present the alert over it, and it would never show.
@Composable
actual fun rememberArchiveHandOff(onDone: (ArchiveHandOffResult) -> Unit): (ExportArchive) -> Unit {
    val done by rememberUpdatedState(onDone)
    val scope = rememberCoroutineScope()
    return remember(scope) {
        { archive ->
            scope.launch {
                val target = FileKit.cacheDir / archive.name
                if (target.exists()) target.delete()
                when (val written = archive.saveTo(target)) {
                    ArchiveHandOffResult.Saved -> {
                        val ran = share(target)
                        // The sheet has copied it wherever it went; a later save writes it again.
                        runSuspendCatching { target.delete() }
                        awaitModalsDismissed()
                        done(if (ran) ArchiveHandOffResult.Shared else ArchiveHandOffResult.Dismissed)
                    }
                    else -> done(written)
                }
            }
        }
    }
}

private suspend fun ExportArchive.saveTo(target: PlatformFile): ArchiveHandOffResult = runSuspendCatching {
    writeTo(target)
}
    .fold({ ArchiveHandOffResult.Saved }, { ArchiveHandOffResult.Failed })

/**
 * Shows the share sheet for [file] and returns once it has closed: whether an activity (Save to Files, AirDrop…) ran. FileKit's share
 * launcher returns as the sheet opens, with no word on how it closed.
 */
private suspend fun share(file: PlatformFile): Boolean {
    awaitModalsDismissed()
    val presenter = topViewController() ?: return false
    return suspendCancellableCoroutine { continuation ->
        val sheet = UIActivityViewController(listOf(NSURL.fileURLWithPath(file.path)), null)
        sheet.completionWithItemsHandler = { _, completed, _, _ -> if (continuation.isActive) continuation.resume(completed) }
        // An iPad shows the sheet as a popover, which needs an anchor.
        sheet.popoverPresentationController?.sourceView = presenter.view
        presenter.presentViewController(sheet, animated = true, completion = null)
    }
}

private fun topViewController(): UIViewController? {
    val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull { it is UIWindowScene } as? UIWindowScene
    var top = scene?.windows?.filterIsInstance<UIWindow>()?.firstOrNull { it.isKeyWindow() }?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
