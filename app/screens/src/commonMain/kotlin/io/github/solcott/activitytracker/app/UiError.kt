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
        LoadSegment,
        LoadStarredSegments,
        UpdateStar,
        LoadLikes,
        LoadComments,
        LoadReplies,
        LoadProfile,
        UpdateFollow,
        UpdateBlock,
        UpdateMute,
        SendReport,
        OpenSubject,
        LoadBlocks,
        LoadMutes,
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
        CombineActivity,
        SplitActivity,
        LoadDataSources,
        SaveDataSource,
        LoadCombineCandidates,
        ReadActivityFile,
        UnsupportedActivityFile,
        SaveSettings,
        SaveProfile,
        SaveZones,
        LoadPrivacyZones,
        SavePrivacyZones,

        /** An athlete already has the most privacy zones they can keep. */
        TooManyZones,

        /** The current location couldn't be found for a new privacy zone. */
        NoLocation,

        /** The session predates access to the athlete's spaces, where non-public details live; signing in again grants it. */
        SignInAgain,

        /** Writes the save depends on haven't been sent yet (offline, or refused for now); trying again later sends them. */
        WritesPending,

        /** The activity's track or streams couldn't be read for an export. */
        ExportActivity,

        /** The activity still has writes waiting to be sent, so it can't be exported yet. */
        ExportNotSent,

        /** The activity's original file couldn't be read for download. */
        DownloadOriginal,

        /** The activity's original file is still waiting to be sent, so it can't be downloaded yet. */
        OriginalNotSent,
    }

    companion object {
        fun of(kind: Kind, cause: Throwable): UiError = UiError(kind, cause.message)
    }
}
