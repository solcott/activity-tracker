package io.github.solcott.activitytracker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The brand (teal, amber accents) in each platform's idiom. Android: Material 3. Desktop and web: Inter, a denser scale, tighter corners
 * and smaller touch targets. [darkTheme] follows the system unless a preview or test sets it.
 */
@Composable
fun AppTheme(modifier: Modifier = Modifier, darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val platform = LocalUiPlatform.current
    MaterialTheme(
        colorScheme = if (darkTheme) BrandDark else BrandLight,
        typography = platformTypography(),
        shapes = if (platform.hasPointer) PointerShapes else Shapes(),
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides if (platform.hasPointer) 40.dp else 48.dp) {
            // Paints the themed background and sets content colors for screens without a Scaffold.
            Surface(modifier.fillMaxSize(), content = content)
        }
    }
}

/** The tighter corners of web and desktop UI. */
private val PointerShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(12.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )
