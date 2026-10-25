package io.github.solcott.activitytracker.app.record

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * Asks for location while in use, and on Android 13+ for notifications too, so the recording's notification shows (record/gps-recording).
 * Only location decides the result: a recording goes on without its notification.
 */
@Composable
actual fun rememberLocationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            onResult(grants[Manifest.permission.ACCESS_FINE_LOCATION] == true)
        }
    val permissions =
        listOfNotNull(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU },
        )
    return { launcher.launch(permissions.toTypedArray()) }
}
