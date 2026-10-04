package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val Orange = Color(0xFFFC5200)

@Composable
fun AppTheme(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors =
        if (isSystemInDarkTheme()) {
            darkColorScheme(primary = Orange, secondary = Orange)
        } else {
            lightColorScheme(primary = Orange, secondary = Orange)
        }
    MaterialTheme(colorScheme = colors) {
        // Paints the themed background and sets content colors for screens without a Scaffold.
        Surface(modifier.fillMaxSize(), content = content)
    }
}
