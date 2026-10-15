package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
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
        val activity: ActivityView,
        val comments: ImmutableList<CommentView>,
        val draftComment: TextFieldState,
        /** The signed-in athlete, who may delete the comments they wrote and no others. */
        val viewerDid: Did,
        /** The activity is the signed-in athlete's own, so it can't be liked. */
        val isOwn: Boolean,
        /** Who liked it, once the viewer has shown the list; null while it's hidden. */
        val likesList: LikesList?,
        /** Why the viewer's last like couldn't be given: a session that can't like a non-public activity is told to sign in again. */
        val likeError: UiError? = null,
        /** Why the viewer's last comment couldn't be sent, likewise, its text back in [draftComment]; or why a delete failed. */
        val commentError: UiError? = null,
        override val eventSink: (ActivityDetailEvent) -> Unit,
        /** Null while loading, and when there are no streams or the viewer may not see them. */
        val charts: ActivityCharts? = null,
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
    Gears,
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

    data object SendComment : ActivityDetailEvent

    /** The viewer deletes their own comment [uri], confirmed. */
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

    /** Opens the activity's media full screen at [index]. */
    data class OpenMedia(val index: Int) : ActivityDetailEvent
}
