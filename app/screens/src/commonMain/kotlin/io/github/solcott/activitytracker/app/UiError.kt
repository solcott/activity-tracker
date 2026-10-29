package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.data.toDataError
import io.github.solcott.dataresult.DataError

/**
 * A failure for a screen to show. Presenters have no UI resources, so they say what failed ([kind]) and why, when the failure was
 * classified ([reason]); the UI shows localized text for both and never the failure's own message.
 */
@Immutable
data class UiError(val kind: Kind, val reason: DataError? = null) {
    enum class Kind {
        LoadFeed,
        LoadActivity,
        LoadSegment,

        /** The owner's full track couldn't be read, so a segment can't be cut from it. */
        LoadRoute,
        LoadStarredSegments,
        LoadLeaderboard,
        LoadMayors,
        UpdateStar,

        /** A new segment's line couldn't be checked for duplicates, so it wasn't published. */
        CheckSegment,

        /** A new segment couldn't be queued to be published. */
        PublishSegment,
        LoadLikes,
        LoadComments,
        LoadReplies,
        LoadProfile,
        LoadActivities,
        UpdateFollow,
        UpdateBlock,
        UpdateMute,
        SendReport,

        /** A segment name proposal, or an edit of one, wasn't sent (segments/segment-names). */
        ProposeSegmentName,
        VoteSegmentName,
        DeleteSegmentName,
        OpenSubject,

        /** Text typed as a moderation subject isn't an AT-URI, so nothing was asked for. */
        InvalidSubjectUri,
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
        LoadGear,

        /** One gear item's page (gear/gear-usage), the athlete's own or someone else's. */
        LoadGearItem,

        /** The activities counted in a gear item's usage. */
        LoadGearActivities,
        SaveGear,
        DeleteGear,
        LoadGearCollection,
        SaveGearCollection,
        DeleteGearCollection,

        /** A mount, unmount or replace couldn't be saved (gear/gear-components). */
        SaveMount,

        /** A mount would overlap one the item already has. */
        MountOverlap,

        /** A mount would make an item mounted on itself. */
        MountLoop,

        /** A mount would go more than three levels below a top-level item. */
        MountTooDeep,

        /** An unmount date isn't after the mount's start. */
        MountEndsBeforeStart,

        /** Gear an import added for a new device, or its mount or collections, couldn't be saved (gear/gear-sensors). */
        SaveNewGear,

        /** The devices the athlete marked as not gear couldn't be read. */
        LoadIgnoredSensors,

        /** A sensor or profile link, or an ignored device, couldn't be changed. */
        SaveGearLinks,

        /** Newly linked gear couldn't be added to every earlier import offered. */
        ApplyGearToPast,

        /** The session predates gear in the athlete's spaces, so it can save Public gear only until they sign in again. */
        GearSignInAgain,

        /** An athlete already has the most privacy zones they can keep. */
        TooManyZones,

        /** The current location couldn't be found for a new privacy zone. */
        NoLocation,

        /** The session predates access to the athlete's spaces, where non-public details live; signing in again grants it. */
        SignInAgain,

        /**
         * The athlete's PDS doesn't host spaces, so a non-public save was refused before anything was written; signing in again can't help.
         */
        SpacesUnsupported,

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

        /** A change the server refused couldn't be sent again (data/outbox). */
        RetryChange,

        /** A change the server refused couldn't be discarded (data/outbox). */
        DiscardChange,
    }

    companion object {
        fun of(kind: Kind, cause: Throwable): UiError = UiError(kind, cause.toDataError())
    }
}
