package io.github.solcott.activitytracker.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/** Which platform's conventions the shared UI follows: its theme, controls and navigation chrome. The iOS app draws its own. */
enum class UiPlatform {
    Android,
    Desktop,
    Web;

    /** Touch-first phones: pull to refresh, bottom tabs, no hover. */
    val isMobile: Boolean
        get() = this == Android

    /** Mouse and keyboard: hover states, a pointer cursor, scrollbars and a refresh button. */
    val hasPointer: Boolean
        get() = !isMobile
}

/** This build's platform. */
internal expect val currentUiPlatform: UiPlatform

/**
 * Read this rather than [currentUiPlatform], so a preview or test can render another platform's theme and navigation shell. The
 * `ui/adaptive` wrappers don't read it: they draw Material's controls on every platform, which the theme adapts.
 */
val LocalUiPlatform = staticCompositionLocalOf { currentUiPlatform }
