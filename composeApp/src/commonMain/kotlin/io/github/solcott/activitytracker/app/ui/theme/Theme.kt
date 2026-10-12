package io.github.solcott.activitytracker.app.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The brand (teal, amber accents) in each platform's idiom. Android: Material 3. iOS: grouped system surfaces, Apple's text styles, rounder
 * corners, no tonal elevation, and dimming instead of ripples. Desktop and web: Inter, a denser scale, tighter corners and smaller touch
 * targets.
 */
@Composable
fun AppTheme(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val platform = LocalUiPlatform.current
    val dark = isSystemInDarkTheme()
    val ios = platform == UiPlatform.Ios
    MaterialTheme(
        colorScheme =
            when {
                ios && dark -> IosDark
                ios -> IosLight
                dark -> BrandDark
                else -> BrandLight
            },
        typography = platformTypography(),
        shapes = platformShapes(platform),
    ) {
        CompositionLocalProvider(
            LocalIndication provides if (ios) OpacityIndication else LocalIndication.current,
            LocalRippleConfiguration provides if (ios) null else LocalRippleConfiguration.current,
            LocalTonalElevationEnabled provides !ios,
            LocalMinimumInteractiveComponentSize provides if (platform.hasPointer) 40.dp else 48.dp,
            LocalIosColors provides if (dark) IosDarkColors else IosLightColors,
        ) {
            // Paints the themed background and sets content colors for screens without a Scaffold.
            Surface(modifier.fillMaxSize(), content = content)
        }
    }
}

private fun platformShapes(platform: UiPlatform): Shapes =
    when (platform) {
        UiPlatform.Android -> Shapes()
        UiPlatform.Ios -> IosShapes
        UiPlatform.Desktop,
        UiPlatform.Web -> PointerShapes
    }

/** UIKit's rounder corners: grouped cells and buttons around 10–12 pt, sheets around 20. */
private val IosShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(22.dp),
    )

/** The tighter corners of web and desktop UI. */
private val PointerShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(12.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )
