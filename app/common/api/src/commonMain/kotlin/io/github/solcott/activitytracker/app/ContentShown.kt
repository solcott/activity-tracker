package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Immutable
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.hasLoaded
import io.github.solcott.uistate.isLoading

/**
 * What a screen shows for content it loads (ui/content-loading), decided once for the shared and the native UIs: progress until the first
 * answer, why it failed when nothing has loaded, and from then on the content, kept through every refresh and failed refresh.
 */
@Immutable
sealed interface ContentShown {
    /** Nothing has answered yet. */
    data object Progress : ContentShown

    /** The first load failed: [error] says what and why, beside a way to try again. */
    @Immutable data class Failure(val error: UiError) : ContentShown

    /**
     * Something has loaded and stays on screen. Over it: [refreshError] when the last refresh failed, beside a way to try again, or
     * progress while [refreshing].
     */
    @Immutable data class Content(val refreshError: UiError?, val refreshing: Boolean) : ContentShown

    companion object {
        /** What to show for [state], whose failures are of [errorKind]. */
        fun of(state: ContentState<*>, errorKind: UiError.Kind): ContentShown {
            val error = state.errorOrNull?.let { UiError(errorKind, it) }
            return when {
                state.hasLoaded -> Content(refreshError = error, refreshing = state.isLoading)
                error != null -> Failure(error)
                else -> Progress
            }
        }
    }
}
