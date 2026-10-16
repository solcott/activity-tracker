package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.solcott.activitytracker.data.activity.ExportedFile
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.dialogs.compose.rememberShareFileLauncher
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

// The share sheet needs a file: write it under its name to the cache dir, which FileKit's merged FileProvider serves on Android.
@Composable
actual fun rememberFileHandOff(): (ExportedFile) -> Unit {
    val share = rememberShareFileLauncher()
    val scope = rememberCoroutineScope()
    return remember(share, scope) {
        { file ->
            scope.launch {
                val target = FileKit.cacheDir / file.name
                target.write(file.bytes)
                awaitModalsDismissed()
                share.launch(target)
            }
        }
    }
}

/**
 * The export sheet closes as the file arrives, and UIKit refuses to present the share sheet while that sheet is still being dismissed
 * ("already presenting"). Wait until nothing is presented over the key window's root, for a second at most.
 */
private suspend fun awaitModalsDismissed() {
    repeat(MAX_CHECKS) {
        val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull { it is UIWindowScene } as? UIWindowScene
        val root = scene?.windows?.filterIsInstance<UIWindow>()?.firstOrNull { it.isKeyWindow() }?.rootViewController
        if (root?.presentedViewController == null) return
        delay(CHECK_INTERVAL_MS)
    }
}

private const val MAX_CHECKS = 20
private const val CHECK_INTERVAL_MS = 50L
