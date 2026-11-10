package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.compositionLocalOf

/**
 * The layout around Settings, which the navigation shell provides; the list marks [SettingsPane.shownPage]'s row. Here and not beside
 * [SettingsPane] in the contracts, which hold no Compose type but the stability annotations (dev/ios-swift-interop).
 */
val LocalSettingsPane = compositionLocalOf { SettingsPane.Single }
