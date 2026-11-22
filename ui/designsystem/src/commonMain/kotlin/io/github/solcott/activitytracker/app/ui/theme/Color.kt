package io.github.solcott.activitytracker.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * The brand ("High country"): violet, with amber for likes and highlights (tertiary). Full Material 3 tonal role sets from HCT tonal
 * palettes of the violet (hue 294.5) and amber (hue 81.4), so nothing falls back to the baseline purple. Light neutrals are plain greys
 * around #FAFAFA rather than violet-tinted ones; dark neutrals carry a faint violet cast. Light tertiary is a dark amber because the
 * bright one (#F5B83D, dark tertiary) is unreadable on light surfaces. The primary is never orange (DESIGN.md).
 */

internal val BrandLight: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF5B3FC4),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE6DEFF),
        onPrimaryContainer = Color(0xFF1D0061),
        inversePrimary = Color(0xFFCABEFF),
        secondary = Color(0xFF605B71),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE6DFF9),
        onSecondaryContainer = Color(0xFF1C192B),
        tertiary = Color(0xFF7C5800),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDEA8),
        onTertiaryContainer = Color(0xFF271900),
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF1C1C1C),
        surface = Color(0xFFFAFAFA),
        onSurface = Color(0xFF1C1C1C),
        surfaceVariant = Color(0xFFE2E2E2),
        onSurfaceVariant = Color(0xFF474747),
        surfaceTint = Color(0xFF5B3FC4),
        inverseSurface = Color(0xFF303030),
        inverseOnSurface = Color(0xFFF1F1F1),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF757575),
        outlineVariant = Color(0xFFC6C6C6),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFAFAFA),
        surfaceDim = Color(0xFFDADADA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF4F4F4),
        surfaceContainer = Color(0xFFEEEEEE),
        surfaceContainerHigh = Color(0xFFE8E8E8),
        surfaceContainerHighest = Color(0xFFE2E2E2),
    )

val BrandDark: ColorScheme =
    darkColorScheme(
        primary = Color(0xFFCABEFF),
        onPrimary = Color(0xFF320099),
        primaryContainer = Color(0xFF4929B2),
        onPrimaryContainer = Color(0xFFE6DEFF),
        inversePrimary = Color(0xFF5B3FC4),
        secondary = Color(0xFFCAC3DC),
        onSecondary = Color(0xFF312E41),
        secondaryContainer = Color(0xFF484458),
        onSecondaryContainer = Color(0xFFE6DFF9),
        tertiary = Color(0xFFF5B83D),
        onTertiary = Color(0xFF422D00),
        tertiaryContainer = Color(0xFF5E4200),
        onTertiaryContainer = Color(0xFFFFDEA8),
        background = Color(0xFF141316),
        onBackground = Color(0xFFE6E1E6),
        surface = Color(0xFF141316),
        onSurface = Color(0xFFE6E1E6),
        surfaceVariant = Color(0xFF48454E),
        onSurfaceVariant = Color(0xFFC9C4D0),
        surfaceTint = Color(0xFFCABEFF),
        inverseSurface = Color(0xFFE6E1E6),
        inverseOnSurface = Color(0xFF313033),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF938F99),
        outlineVariant = Color(0xFF48454E),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF3A383C),
        surfaceDim = Color(0xFF141316),
        surfaceContainerLowest = Color(0xFF0F0E11),
        surfaceContainerLow = Color(0xFF1C1B1E),
        surfaceContainer = Color(0xFF201F23),
        surfaceContainerHigh = Color(0xFF2B292D),
        surfaceContainerHighest = Color(0xFF363438),
    )
