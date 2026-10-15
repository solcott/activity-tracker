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
        LoadLikes,
        LoadComments,
        LoadProfile,
        UpdateFollow,
        LoadFollowers,
        LoadFollowing,
        LoadFollowRequests,
        AnswerFollowRequest,
        RemoveFollower,
        SearchAthletes,
        SaveActivity,
        DeleteActivity,
        DeleteComment,
        HideComment,
        UnhideComment,
        UngroupActivity,
        ReadActivityFile,
        UnsupportedActivityFile,
        SaveSettings,
        SaveProfile,
        SaveZones,

        /** The session predates access to the athlete's spaces, where non-public details live; signing in again grants it. */
        SignInAgain,
    }

    companion object {
        fun of(kind: Kind, cause: Throwable): UiError = UiError(kind, cause.message)
    }
}
