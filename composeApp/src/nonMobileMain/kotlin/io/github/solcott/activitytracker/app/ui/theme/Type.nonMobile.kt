package io.github.solcott.activitytracker.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.inter_bold
import io.github.solcott.activitytracker.composeApp.resources.inter_medium
import io.github.solcott.activitytracker.composeApp.resources.inter_regular
import io.github.solcott.activitytracker.composeApp.resources.inter_semibold
import org.jetbrains.compose.resources.Font

/** A denser, web-style scale in Inter (bundled only for desktop and web; OFL, see `files/inter_license.txt`). */
@Composable
internal actual fun platformTypography(): Typography {
    val inter =
        FontFamily(
            Font(Res.font.inter_regular, FontWeight.Normal),
            Font(Res.font.inter_medium, FontWeight.Medium),
            Font(Res.font.inter_semibold, FontWeight.SemiBold),
            Font(Res.font.inter_bold, FontWeight.Bold),
        )
    return remember(inter) { PointerTypography.inFamily(inter) }
}

/** The scale's sizes; [platformTypography] sets the family once the fonts load. */
private val PointerTypography =
    Typography(
        displayLarge = style(48, 56, FontWeight.SemiBold, -0.5),
        displayMedium = style(40, 48, FontWeight.SemiBold, -0.4),
        displaySmall = style(34, 40, FontWeight.SemiBold, -0.3),
        headlineLarge = style(30, 36, FontWeight.SemiBold, -0.3),
        headlineMedium = style(26, 32, FontWeight.SemiBold, -0.2),
        headlineSmall = style(22, 28, FontWeight.SemiBold, -0.2),
        titleLarge = style(20, 28, FontWeight.SemiBold, -0.1),
        titleMedium = style(15, 22, FontWeight.SemiBold),
        titleSmall = style(14, 20, FontWeight.Medium),
        bodyLarge = style(15, 22, FontWeight.Normal),
        bodyMedium = style(14, 20, FontWeight.Normal),
        bodySmall = style(12, 16, FontWeight.Normal),
        labelLarge = style(14, 20, FontWeight.Medium),
        labelMedium = style(12, 16, FontWeight.Medium),
        labelSmall = style(11, 16, FontWeight.Medium),
    )

private fun style(size: Int, lineHeight: Int, weight: FontWeight, tracking: Double = 0.0) =
    TextStyle(fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight, letterSpacing = tracking.sp)

private fun Typography.inFamily(family: FontFamily): Typography {
    fun TextStyle.inFamily() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.inFamily(),
        displayMedium = displayMedium.inFamily(),
        displaySmall = displaySmall.inFamily(),
        headlineLarge = headlineLarge.inFamily(),
        headlineMedium = headlineMedium.inFamily(),
        headlineSmall = headlineSmall.inFamily(),
        titleLarge = titleLarge.inFamily(),
        titleMedium = titleMedium.inFamily(),
        titleSmall = titleSmall.inFamily(),
        bodyLarge = bodyLarge.inFamily(),
        bodyMedium = bodyMedium.inFamily(),
        bodySmall = bodySmall.inFamily(),
        labelLarge = labelLarge.inFamily(),
        labelMedium = labelMedium.inFamily(),
        labelSmall = labelSmall.inFamily(),
    )
}
