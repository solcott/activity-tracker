package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Immutable

/**
 * A failure for a screen to show. Presenters have no UI resources, so they say what failed ([kind]) and pass along the failure's own
 * message ([detail]); the UI shows [detail] when there is one and localized text for [kind] otherwise.
 */
@Immutable
data class UiError(val kind: Kind, val detail: String?) {
    enum class Kind {
        LoadFeed,
        LoadActivity,
        LoadKudos,
        LoadProfile,
        LoadFollowers,
        LoadFollowing,
        SaveActivity,
        SaveSettings,
        SaveProfile,
    }

    companion object {
        fun of(kind: Kind, cause: Throwable): UiError = UiError(kind, cause.message)
    }
}
