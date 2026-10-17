package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.activity.ExportFormat
import io.github.solcott.activitytracker.data.activity.ExportedFile
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.SentComment
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@CircuitSerializable(SessionScope::class) data class ActivityDetailScreen(val uri: String) : Screen

@Immutable
sealed interface ActivityDetailState : CircuitUiState {
    val eventSink: (ActivityDetailEvent) -> Unit

    @Immutable data class Loading(override val eventSink: (ActivityDetailEvent) -> Unit) : ActivityDetailState

    @Immutable data class Error(val error: UiError, override val eventSink: (ActivityDetailEvent) -> Unit) : ActivityDetailState

    @Immutable
    data class Loaded(
        /** Its comment count already moved by the viewer's own comment changes the AppView hasn't caught up with. */
        val activity: ActivityView,
        /** The threads loaded so far, oldest first, as the viewer left them. */
        val comments: CommentsPaging,
        /** The viewer's top-level comments the AppView doesn't list yet, shown after [comments]; sent replies are in their thread. */
        val sentComments: ImmutableList<SentComment>,
        val draftComment: TextFieldState,
        /** The listed comment or reply [draftComment] answers; null while it's a top-level comment. */
        val replyingTo: CommentView? = null,
        /** The signed-in athlete, who may delete the comments they wrote and no others. */
        val viewerDid: Did,
        /** The activity is the signed-in athlete's own, so it can't be liked. */
        val isOwn: Boolean,
        /** Who liked it, once the viewer has shown the list; null while it's hidden. */
        val likesList: LikesList?,
        /** Why the viewer's last like couldn't be given: a session that can't like a non-public activity is told to sign in again. */
        val likeError: UiError? = null,
        /**
         * Why the viewer's last comment couldn't be sent, likewise, its text back in [draftComment] and [replyingTo] kept; or why a delete
         * failed.
         */
        val commentError: UiError? = null,
        override val eventSink: (ActivityDetailEvent) -> Unit,
        /** Null while loading, and when there are no streams or the viewer may not see them. */
        val charts: ActivityCharts? = null,
        /** A pool swim's pool length, published with the activity, so shown to anyone who may see it; null for anything else. */
        val poolLength: PoolLength? = null,
        /** The viewer's own activity has heart rate or power but no zones, because they hadn't set thresholds. */
        val chartsMissingZones: Boolean = false,
        /** The owner is changing who may see the charts. */
        val savingChartsVisibility: Boolean = false,
        /** The owner approves followers, so Followers means only the ones they approved. */
        val approvesFollowers: Boolean = false,
        /** The collapsible sections this activity has data for, in the order they show. */
        val sections: ImmutableList<DetailSection> = persistentListOf(),
        /** The sections showing in full; the rest show an overview. */
        val expanded: ImmutableSet<DetailSection> = persistentSetOf(),
        /** What the owner can do to the activity; null on someone else's. */
        val manage: ActivityManage? = null,
        /**
         * The owner's full route, untrimmed by their privacy zones, from the track only they read; null on someone else's activity or until
         * it loads. The map draws it over the record's trimmed polyline.
         */
        val ownRoute: String? = null,
        /** The owner can add a privacy zone at the route's start or end: the track was read. */
        val canHideRouteEnds: Boolean = false,
        /** A zone was just added from this activity. */
        val zoneAdded: Boolean = false,
        val routeError: UiError? = null,
        /**
         * Exporting the activity as a file; null on someone else's activity, or when it has no track or streams to rebuild a file from and
         * no original file.
         */
        val export: ActivityExportState? = null,
    ) : ActivityDetailState {
        val anyExpanded: Boolean
            get() = sections.any { it in expanded }
    }
}

/** A collapsible part of the detail screen, below the overview that always shows. In the order they show. */
enum class DetailSection {
    HeartRate,
    Power,
    Speed,
    Cadence,
    /** Elevation and grade. */
    Elevation,
    Temperature,
    Depth,
    /** A dive's ppO₂ and CNS. */
    Oxygen,
    TankPressure,
    Respiration,
    /** Vertical oscillation, stance time and its balance, step length and vertical ratio. */
    RunningDynamics,
    Gears,
    /** A multisport activity's legs and transitions. */
    Legs,
    /** A pool swim's lengths. */
    Lengths,
    /** A workout's sets. */
    Sets,
    Laps,
    /** The recording device and its sensors. */
    Device,
}

/** Deleting or ungrouping the signed-in athlete's own activity. */
@Immutable
data class ActivityManage(
    /** The other recordings of this activity, which deleting it can take too or leave as activities of their own. */
    val otherRecordings: Int,
    /** The activity is grouped with exactly one other, its main activity or its one recording, so the two can be split apart. */
    val canUngroup: Boolean,
    /** A delete or ungroup is running. */
    val busy: Boolean,
    val error: UiError?,
)

/**
 * Exporting the signed-in athlete's own activity as a file rebuilt from its record, streams and full track, or as the [original] file it
 * was imported from.
 */
@Immutable
data class ActivityExportState(
    /** What the stored data allows, in [ExportFormat] order; empty only when there's an [original]. */
    val formats: ImmutableList<ExportFormat>,
    /** An export is being built. */
    val busy: Boolean = false,
    /** Why the last export failed: not sent yet ([UiError.Kind.ExportNotSent]) or unreadable. */
    val error: UiError? = null,
    /** The file built, waiting for the UI to hand to the platform and send [ActivityDetailEvent.ExportHandedOff]. */
    val file: ExportedFile? = null,
    /** The activity's streams hold laps, so the file has them and the note doesn't say it lacks them. */
    val hasLaps: Boolean = false,
    /** The file the activity was imported from, stored or still queued; null for one recorded in the app or entered by hand. */
    val original: OriginalFile? = null,
)

/** An imported activity's original file: its [name] as picked and its [format] (a `feed.activitySource#format` token). */
@Immutable data class OriginalFile(val name: String, val format: String)

/** An activity's comment threads, loaded a page at a time as the viewer scrolls to the end. */
@Immutable
data class CommentsPaging(
    val items: ImmutableList<CommentThread> = persistentListOf(),
    /** A page is loading. */
    val isLoading: Boolean = false,
    /** Every page is loaded. */
    val endReached: Boolean = false,
    /** Why the last page couldn't be loaded; [ActivityDetailEvent.RetryComments] tries it again. */
    val error: UiError? = null,
)

/**
 * A thread on the detail screen, as the viewer left it: a top-level comment and its replies, indented under it. Threads are one level deep,
 * so a reply to a reply is listed here too.
 */
@Immutable
data class CommentThread(
    /** The top-level comment's URI. */
    val uri: AtUri,
    /** The top-level comment; null for the placeholder shown when it isn't listed for the viewer, which says nothing more about it. */
    val root: CommentView?,
    /** The replies loaded so far, oldest first: its first few, then each page of more the viewer asked for. */
    val replies: ImmutableList<CommentView>,
    /** The viewer's replies in this thread the AppView doesn't list yet, after [replies]. */
    val sentReplies: ImmutableList<SentComment> = persistentListOf(),
    /** Replies not loaded yet: "View n more replies", which sends [ActivityDetailEvent.LoadMoreReplies]. */
    val moreReplies: Int = 0,
    val repliesState: RepliesState = RepliesState.Idle,
)

/** Loading more of a thread's replies. */
@Immutable
sealed interface RepliesState {
    data object Idle : RepliesState

    data object Loading : RepliesState

    /** The last page couldn't be loaded; [ActivityDetailEvent.LoadMoreReplies] tries it again. */
    data class Failed(val error: UiError) : RepliesState
}

@Immutable
data class LikesList(
    val athletes: ImmutableList<ProfileViewBasic>,
    val isLoading: Boolean,
    val error: UiError?,
)

sealed interface ActivityDetailEvent {
    data object Back : ActivityDetailEvent

    data object Retry : ActivityDetailEvent

    data object ToggleLike : ActivityDetailEvent

    data object OpenAuthor : ActivityDetailEvent

    /** Shows or hides who liked it; showing it reloads the list. */
    data object ToggleLikesList : ActivityDetailEvent

    data class OpenProfile(val did: Did) : ActivityDetailEvent

    /** Opens another recording of this activity, or the activity this one is a recording of. */
    data class OpenActivity(val uri: AtUri) : ActivityDetailEvent

    /** Sends the draft: a reply to [ActivityDetailState.Loaded.replyingTo] when set, else a top-level comment. */
    data object SendComment : ActivityDetailEvent

    /** The viewer scrolled near the end of the listed comments: the next page loads, unless one is loading, failed or none is left. */
    data object LoadMoreComments : ActivityDetailEvent

    /** Loads again the page of comments that failed. */
    data object RetryComments : ActivityDetailEvent

    /** The viewer replies to the listed comment or reply [uri]: the comment field answers it until it's sent or [CancelReply]. */
    data class ReplyTo(val uri: AtUri) : ActivityDetailEvent

    /** The comment field goes back to a top-level comment, keeping its text. */
    data object CancelReply : ActivityDetailEvent

    /** Loads the next page of [thread]'s replies, or again the one that failed, unless one is loading or none is left. */
    data class LoadMoreReplies(val thread: AtUri) : ActivityDetailEvent

    /** The viewer deletes their own comment [uri], listed or just sent, confirmed. */
    data class DeleteComment(val uri: AtUri) : ActivityDetailEvent

    /** The owner hides someone else's comment [uri] on their activity: it stays listed for them, marked, and leaves the count. */
    data class HideComment(val uri: AtUri) : ActivityDetailEvent

    /** The owner unhides a comment [uri] they hid. */
    data class UnhideComment(val uri: AtUri) : ActivityDetailEvent

    /** The owner changes who may see the charts: a `Visibility` value. */
    data class SetChartsVisibility(val visibility: String) : ActivityDetailEvent

    /** Opens the training zones screen, to set the thresholds zone charts need. */
    data object OpenTrainingZones : ActivityDetailEvent

    data class ToggleSection(val section: DetailSection) : ActivityDetailEvent

    /** Expand every section when none is expanded, else collapse them all. */
    data object ToggleAll : ActivityDetailEvent

    /** The owner deletes the activity, confirmed, and its other recordings too [withRecordings] or else leaves them as activities. */
    data class Delete(val withRecordings: Boolean) : ActivityDetailEvent

    /** The owner splits the two activities of a group apart: see [ActivityManage.canUngroup]. */
    data object Ungroup : ActivityDetailEvent

    /** The owner edits the activity's title, description, dive site and photos. */
    data object Edit : ActivityDetailEvent

    /** The owner adds a privacy zone at the route's start: see [Loaded.canHideRouteEnds]. */
    data object HideStart : ActivityDetailEvent

    /** The owner adds a privacy zone at the route's end. */
    data object HideEnd : ActivityDetailEvent

    /** Opens the activity's media full screen at [index]. */
    data class OpenMedia(val index: Int) : ActivityDetailEvent

    /** The owner exports the activity as [format]: see [ActivityExportState]. */
    data class Export(val format: ExportFormat) : ActivityDetailEvent

    /** The owner downloads the file the activity was imported from ([ActivityExportState.original]), byte for byte. */
    data object DownloadOriginal : ActivityDetailEvent

    /** The UI handed [ActivityExportState.file] to the platform, whether it was shared, saved or cancelled. */
    data object ExportHandedOff : ActivityDetailEvent
}
