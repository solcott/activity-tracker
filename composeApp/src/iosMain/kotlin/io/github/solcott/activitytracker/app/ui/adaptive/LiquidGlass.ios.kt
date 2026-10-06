package io.github.solcott.activitytracker.app.ui.adaptive

import platform.UIKit.UIDevice

internal actual val usesLiquidGlass: Boolean =
    (UIDevice.currentDevice.systemVersion.substringBefore('.').toIntOrNull() ?: 0) >= LIQUID_GLASS_MAJOR_VERSION

private const val LIQUID_GLASS_MAJOR_VERSION = 26
