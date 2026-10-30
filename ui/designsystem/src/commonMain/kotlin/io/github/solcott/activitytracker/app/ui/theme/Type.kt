package io.github.solcott.activitytracker.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

/** Android: Material 3's type scale. iOS: Apple's text styles in the system font. Desktop and web: a denser scale in Inter. */
@Composable internal expect fun platformTypography(): Typography
