package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.error_answer_follow_request
import io.github.solcott.activitytracker.composeApp.resources.error_check_segment
import io.github.solcott.activitytracker.composeApp.resources.error_combine_activity
import io.github.solcott.activitytracker.composeApp.resources.error_delete_activity
import io.github.solcott.activitytracker.composeApp.resources.error_delete_comment
import io.github.solcott.activitytracker.composeApp.resources.error_delete_gear
import io.github.solcott.activitytracker.composeApp.resources.error_delete_gear_collection
import io.github.solcott.activitytracker.composeApp.resources.error_download_original
import io.github.solcott.activitytracker.composeApp.resources.error_export_activity
import io.github.solcott.activitytracker.composeApp.resources.error_export_not_sent
import io.github.solcott.activitytracker.composeApp.resources.error_gear_sign_in_again
import io.github.solcott.activitytracker.composeApp.resources.error_hide_comment
import io.github.solcott.activitytracker.composeApp.resources.error_invalid_subject_uri
import io.github.solcott.activitytracker.composeApp.resources.error_load_activities
import io.github.solcott.activitytracker.composeApp.resources.error_load_activity
import io.github.solcott.activitytracker.composeApp.resources.error_load_blocks
import io.github.solcott.activitytracker.composeApp.resources.error_load_combine_candidates
import io.github.solcott.activitytracker.composeApp.resources.error_load_comments
import io.github.solcott.activitytracker.composeApp.resources.error_load_data_sources
import io.github.solcott.activitytracker.composeApp.resources.error_load_feed
import io.github.solcott.activitytracker.composeApp.resources.error_load_follow_requests
import io.github.solcott.activitytracker.composeApp.resources.error_load_followers
import io.github.solcott.activitytracker.composeApp.resources.error_load_following
import io.github.solcott.activitytracker.composeApp.resources.error_load_gear
import io.github.solcott.activitytracker.composeApp.resources.error_load_gear_activities
import io.github.solcott.activitytracker.composeApp.resources.error_load_gear_collection
import io.github.solcott.activitytracker.composeApp.resources.error_load_gear_item
import io.github.solcott.activitytracker.composeApp.resources.error_load_leaderboard
import io.github.solcott.activitytracker.composeApp.resources.error_load_likes
import io.github.solcott.activitytracker.composeApp.resources.error_load_mayors
import io.github.solcott.activitytracker.composeApp.resources.error_load_mutes
import io.github.solcott.activitytracker.composeApp.resources.error_load_privacy_zones
import io.github.solcott.activitytracker.composeApp.resources.error_load_profile
import io.github.solcott.activitytracker.composeApp.resources.error_load_replies
import io.github.solcott.activitytracker.composeApp.resources.error_load_route
import io.github.solcott.activitytracker.composeApp.resources.error_load_segment
import io.github.solcott.activitytracker.composeApp.resources.error_load_starred_segments
import io.github.solcott.activitytracker.composeApp.resources.error_mount_ends_before_start
import io.github.solcott.activitytracker.composeApp.resources.error_mount_loop
import io.github.solcott.activitytracker.composeApp.resources.error_mount_overlap
import io.github.solcott.activitytracker.composeApp.resources.error_mount_too_deep
import io.github.solcott.activitytracker.composeApp.resources.error_no_location
import io.github.solcott.activitytracker.composeApp.resources.error_open_subject
import io.github.solcott.activitytracker.composeApp.resources.error_original_not_sent
import io.github.solcott.activitytracker.composeApp.resources.error_publish_segment
import io.github.solcott.activitytracker.composeApp.resources.error_read_activity_file
import io.github.solcott.activitytracker.composeApp.resources.error_reason_offline
import io.github.solcott.activitytracker.composeApp.resources.error_reason_server
import io.github.solcott.activitytracker.composeApp.resources.error_reason_unreadable
import io.github.solcott.activitytracker.composeApp.resources.error_remove_follower
import io.github.solcott.activitytracker.composeApp.resources.error_save_activity
import io.github.solcott.activitytracker.composeApp.resources.error_save_data_source
import io.github.solcott.activitytracker.composeApp.resources.error_save_gear
import io.github.solcott.activitytracker.composeApp.resources.error_save_gear_collection
import io.github.solcott.activitytracker.composeApp.resources.error_save_mount
import io.github.solcott.activitytracker.composeApp.resources.error_save_privacy_zones
import io.github.solcott.activitytracker.composeApp.resources.error_save_profile
import io.github.solcott.activitytracker.composeApp.resources.error_save_settings
import io.github.solcott.activitytracker.composeApp.resources.error_save_zones
import io.github.solcott.activitytracker.composeApp.resources.error_search_athletes
import io.github.solcott.activitytracker.composeApp.resources.error_send_report
import io.github.solcott.activitytracker.composeApp.resources.error_sign_in_again
import io.github.solcott.activitytracker.composeApp.resources.error_spaces_unsupported
import io.github.solcott.activitytracker.composeApp.resources.error_split_activity
import io.github.solcott.activitytracker.composeApp.resources.error_too_many_zones
import io.github.solcott.activitytracker.composeApp.resources.error_ungroup_activity
import io.github.solcott.activitytracker.composeApp.resources.error_unhide_comment
import io.github.solcott.activitytracker.composeApp.resources.error_unsupported_activity_file
import io.github.solcott.activitytracker.composeApp.resources.error_update_block
import io.github.solcott.activitytracker.composeApp.resources.error_update_follow
import io.github.solcott.activitytracker.composeApp.resources.error_update_mute
import io.github.solcott.activitytracker.composeApp.resources.error_update_star
import io.github.solcott.activitytracker.composeApp.resources.error_with_reason
import io.github.solcott.activitytracker.composeApp.resources.error_writes_pending
import io.github.solcott.dataresult.DataError
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * An error to show: what failed and, when it's one the athlete can act on or wait out, why. Never the failure's own text, status code or
 * error name.
 */
@Composable fun errorText(error: UiError): String = failureText(kindText(error.kind), error.reason)

/** [what] failed, followed by why when [reason] is one the athlete can act on or wait out. */
@Composable
fun failureText(what: String, reason: DataError?): String =
    reason?.let(::errorReason)?.let { stringResource(Res.string.error_with_reason, what, stringResource(it)) } ?: what

/** Why something failed, for the failures the athlete can act on or wait out; null for the rest, which say only what failed. */
internal fun errorReason(error: DataError): StringResource? =
    when (error) {
        DataError.Network -> Res.string.error_reason_offline
        is DataError.Http if error.code >= SERVER_ERROR -> Res.string.error_reason_server
        is DataError.Api if error.code == INTERNAL_SERVER_ERROR -> Res.string.error_reason_server
        DataError.Serialization -> Res.string.error_reason_unreadable
        else -> null
    }

/** The lowest HTTP status that means the server failed. */
private const val SERVER_ERROR = 500

/** The XRPC error name for a failure on the server. */
private const val INTERNAL_SERVER_ERROR = "InternalServerError"

/** Localized text for what failed. */
@Composable
private fun kindText(kind: UiError.Kind): String =
    stringResource(
        when (kind) {
            UiError.Kind.LoadFeed -> Res.string.error_load_feed
            UiError.Kind.LoadActivity -> Res.string.error_load_activity
            UiError.Kind.LoadSegment -> Res.string.error_load_segment
            UiError.Kind.LoadRoute -> Res.string.error_load_route
            UiError.Kind.LoadStarredSegments -> Res.string.error_load_starred_segments
            UiError.Kind.LoadLeaderboard -> Res.string.error_load_leaderboard
            UiError.Kind.LoadMayors -> Res.string.error_load_mayors
            UiError.Kind.UpdateStar -> Res.string.error_update_star
            UiError.Kind.CheckSegment -> Res.string.error_check_segment
            UiError.Kind.PublishSegment -> Res.string.error_publish_segment
            UiError.Kind.LoadLikes -> Res.string.error_load_likes
            UiError.Kind.LoadComments -> Res.string.error_load_comments
            UiError.Kind.LoadReplies -> Res.string.error_load_replies
            UiError.Kind.LoadProfile -> Res.string.error_load_profile
            UiError.Kind.LoadActivities -> Res.string.error_load_activities
            UiError.Kind.UpdateFollow -> Res.string.error_update_follow
            UiError.Kind.UpdateBlock -> Res.string.error_update_block
            UiError.Kind.SendReport -> Res.string.error_send_report
            UiError.Kind.OpenSubject -> Res.string.error_open_subject
            UiError.Kind.InvalidSubjectUri -> Res.string.error_invalid_subject_uri
            UiError.Kind.UpdateMute -> Res.string.error_update_mute
            UiError.Kind.LoadBlocks -> Res.string.error_load_blocks
            UiError.Kind.LoadMutes -> Res.string.error_load_mutes
            UiError.Kind.LoadFollowers -> Res.string.error_load_followers
            UiError.Kind.LoadFollowing -> Res.string.error_load_following
            UiError.Kind.LoadFollowRequests -> Res.string.error_load_follow_requests
            UiError.Kind.AnswerFollowRequest -> Res.string.error_answer_follow_request
            UiError.Kind.RemoveFollower -> Res.string.error_remove_follower
            UiError.Kind.SearchAthletes -> Res.string.error_search_athletes
            UiError.Kind.SaveActivity -> Res.string.error_save_activity
            UiError.Kind.DeleteActivity -> Res.string.error_delete_activity
            UiError.Kind.DeleteComment -> Res.string.error_delete_comment
            UiError.Kind.HideComment -> Res.string.error_hide_comment
            UiError.Kind.UnhideComment -> Res.string.error_unhide_comment
            UiError.Kind.UngroupActivity -> Res.string.error_ungroup_activity
            UiError.Kind.CombineActivity -> Res.string.error_combine_activity
            UiError.Kind.SplitActivity -> Res.string.error_split_activity
            UiError.Kind.LoadDataSources -> Res.string.error_load_data_sources
            UiError.Kind.SaveDataSource -> Res.string.error_save_data_source
            UiError.Kind.LoadCombineCandidates -> Res.string.error_load_combine_candidates
            UiError.Kind.ReadActivityFile -> Res.string.error_read_activity_file
            UiError.Kind.UnsupportedActivityFile -> Res.string.error_unsupported_activity_file
            UiError.Kind.SaveSettings -> Res.string.error_save_settings
            UiError.Kind.SaveProfile -> Res.string.error_save_profile
            UiError.Kind.SaveZones -> Res.string.error_save_zones
            UiError.Kind.LoadPrivacyZones -> Res.string.error_load_privacy_zones
            UiError.Kind.SavePrivacyZones -> Res.string.error_save_privacy_zones
            UiError.Kind.LoadGear -> Res.string.error_load_gear
            UiError.Kind.LoadGearItem -> Res.string.error_load_gear_item
            UiError.Kind.LoadGearActivities -> Res.string.error_load_gear_activities
            UiError.Kind.SaveGear -> Res.string.error_save_gear
            UiError.Kind.DeleteGear -> Res.string.error_delete_gear
            UiError.Kind.LoadGearCollection -> Res.string.error_load_gear_collection
            UiError.Kind.SaveGearCollection -> Res.string.error_save_gear_collection
            UiError.Kind.DeleteGearCollection -> Res.string.error_delete_gear_collection
            UiError.Kind.SaveMount -> Res.string.error_save_mount
            UiError.Kind.MountOverlap -> Res.string.error_mount_overlap
            UiError.Kind.MountLoop -> Res.string.error_mount_loop
            UiError.Kind.MountTooDeep -> Res.string.error_mount_too_deep
            UiError.Kind.MountEndsBeforeStart -> Res.string.error_mount_ends_before_start
            UiError.Kind.GearSignInAgain -> Res.string.error_gear_sign_in_again
            UiError.Kind.TooManyZones -> Res.string.error_too_many_zones
            UiError.Kind.NoLocation -> Res.string.error_no_location
            UiError.Kind.SignInAgain -> Res.string.error_sign_in_again
            UiError.Kind.SpacesUnsupported -> Res.string.error_spaces_unsupported
            UiError.Kind.WritesPending -> Res.string.error_writes_pending
            UiError.Kind.ExportActivity -> Res.string.error_export_activity
            UiError.Kind.ExportNotSent -> Res.string.error_export_not_sent
            UiError.Kind.DownloadOriginal -> Res.string.error_download_original
            UiError.Kind.OriginalNotSent -> Res.string.error_original_not_sent
        }
    )
