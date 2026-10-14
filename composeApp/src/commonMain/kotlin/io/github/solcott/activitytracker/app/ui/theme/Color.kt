package io.github.solcott.activitytracker.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * The brand: deep teal, with amber for likes and highlights (tertiary). Full Material 3 tonal role sets, so nothing falls back to the
 * baseline purple. Light tertiary is a dark amber because the bright one (#F2B705, dark tertiary) is unreadable on light surfaces.
 */

internal val BrandLight: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF00796B),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFA2F2E2),
        onPrimaryContainer = Color(0xFF00201C),
        inversePrimary = Color(0xFF82D5C6),
        secondary = Color(0xFF4A635F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCCE8E2),
        onSecondaryContainer = Color(0xFF05201C),
        tertiary = Color(0xFF7A5900),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDEA0),
        onTertiaryContainer = Color(0xFF261A00),
        background = Color(0xFFF5FBF9),
        onBackground = Color(0xFF171D1C),
        surface = Color(0xFFF5FBF9),
        onSurface = Color(0xFF171D1C),
        surfaceVariant = Color(0xFFDAE5E1),
        onSurfaceVariant = Color(0xFF3F4947),
        surfaceTint = Color(0xFF00796B),
        inverseSurface = Color(0xFF2B3230),
        inverseOnSurface = Color(0xFFECF2EF),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF6F7977),
        outlineVariant = Color(0xFFBEC9C6),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF5FBF9),
        surfaceDim = Color(0xFFD5DBD9),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFEFF5F3),
        surfaceContainer = Color(0xFFE9EFED),
        surfaceContainerHigh = Color(0xFFE3EAE7),
        surfaceContainerHighest = Color(0xFFDEE4E2),
    )

internal val BrandDark: ColorScheme =
    darkColorScheme(
        primary = Color(0xFF4FD8C4),
        onPrimary = Color(0xFF00382F),
        primaryContainer = Color(0xFF005046),
        onPrimaryContainer = Color(0xFFA2F2E2),
        inversePrimary = Color(0xFF00796B),
        secondary = Color(0xFFB1CCC6),
        onSecondary = Color(0xFF1C3531),
        secondaryContainer = Color(0xFF334B47),
        onSecondaryContainer = Color(0xFFCCE8E2),
        tertiary = Color(0xFFF2B705),
        onTertiary = Color(0xFF3F2E00),
        tertiaryContainer = Color(0xFF5B4300),
        onTertiaryContainer = Color(0xFFFFDEA0),
        background = Color(0xFF0E1514),
        onBackground = Color(0xFFDEE4E1),
        surface = Color(0xFF0E1514),
        onSurface = Color(0xFFDEE4E1),
        surfaceVariant = Color(0xFF3F4947),
        onSurfaceVariant = Color(0xFFBEC9C6),
        surfaceTint = Color(0xFF4FD8C4),
        inverseSurface = Color(0xFFDEE4E1),
        inverseOnSurface = Color(0xFF2B3230),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF899390),
        outlineVariant = Color(0xFF3F4947),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF343B39),
        surfaceDim = Color(0xFF0E1514),
        surfaceContainerLowest = Color(0xFF090F0E),
        surfaceContainerLow = Color(0xFF171D1C),
        surfaceContainer = Color(0xFF1B2120),
        surfaceContainerHigh = Color(0xFF252B2A),
        surfaceContainerHighest = Color(0xFF303635),
    )

/**
 * The brand on iOS's grouped system surfaces: grey screens with white (or near-black) cards, iOS labels and separators, and the system red.
 * Cards read `surfaceContainerHighest`, so that's the iOS cell colour.
 */
internal val IosLight: ColorScheme =
    BrandLight.copy(
        background = Color(0xFFF2F2F7),
        onBackground = Color(0xFF000000),
        surface = Color(0xFFF2F2F7),
        onSurface = Color(0xFF000000),
        onSurfaceVariant = Color(0xFF8A8A8E),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFE5E5EA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFE5E5EA),
        surfaceContainerHighest = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE5E5EA),
        outline = Color(0xFFC6C6C8),
        outlineVariant = Color(0xFFC6C6C8),
        error = Color(0xFFFF3B30),
        onError = Color(0xFFFFFFFF),
    )

internal val IosDark: ColorScheme =
    BrandDark.copy(
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFF98989F),
        surfaceBright = Color(0xFF2C2C2E),
        surfaceDim = Color(0xFF000000),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF1C1C1E),
        surfaceContainer = Color(0xFF1C1C1E),
        surfaceContainerHigh = Color(0xFF2C2C2E),
        surfaceContainerHighest = Color(0xFF1C1C1E),
        surfaceVariant = Color(0xFF2C2C2E),
        outline = Color(0xFF545458),
        outlineVariant = Color(0xFF38383A),
        error = Color(0xFFFF453A),
        onError = Color(0xFFFFFFFF),
    )

/**
 * UIKit system colours with no Material role, for the iOS look of the adaptive components. Read through [LocalIosColors]; [AppTheme]
 * provides the light or dark set.
 */
@Immutable
data class IosColors(
    /** `tertiarySystemFill`: translucent, so an unselected chip shows on a page and on a sheet alike. */
    val tertiaryFill: Color
)

internal val IosLightColors = IosColors(tertiaryFill = Color(0x1F767680))

internal val IosDarkColors = IosColors(tertiaryFill = Color(0x3D767680))

val LocalIosColors = staticCompositionLocalOf { IosLightColors }
