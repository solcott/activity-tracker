package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.ui.graphics.Color
import platform.UIKit.UIColor

/** This colour for a native view. */
internal fun Color.toUIColor(): UIColor =
    UIColor.colorWithRed(red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())
