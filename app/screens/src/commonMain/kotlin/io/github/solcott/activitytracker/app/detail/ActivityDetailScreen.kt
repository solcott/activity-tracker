package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
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
        /** The activity is the signed-in athlete's own, so it can't take kudos. */
        val isOwn: Boolean,
        /** Who gave kudos, once the viewer has shown the list; null while it's hidden. */
        val kudosList: KudosList?,
        override val eventSink: (ActivityDetailEvent) -> Unit,
        /** Null while loading, and when there are no streams or the viewer may not see them. */
        val charts: ActivityCharts? = null,
        /** The viewer's own activity has heart rate or power but no zones, because they hadn't set thresholds. */
        val chartsMissingZones: Boolean = false,
        /** The owner is changing who may see the charts. */
        val savingChartsVisibility: Boolean = false,
        /** The collapsible sections this activity has data for, in the order they show. */
        val sections: ImmutableList<DetailSection> = persistentListOf(),
        /** The sections showing in full; the rest show an overview. */
        val expanded: ImmutableSet<DetailSection> = persistentSetOf(),
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

@Immutable
data class KudosList(
    val athletes: ImmutableList<ProfileViewBasic>,
    val isLoading: Boolean,
    val error: UiError?,
)

sealed interface ActivityDetailEvent {
    data object Back : ActivityDetailEvent

    data object Retry : ActivityDetailEvent

    data object ToggleKudos : ActivityDetailEvent

    data object OpenAuthor : ActivityDetailEvent

    /** Shows or hides who gave kudos; showing it reloads the list. */
    data object ToggleKudosList : ActivityDetailEvent

    data class OpenProfile(val did: Did) : ActivityDetailEvent

    data object SendComment : ActivityDetailEvent

    /** The owner changes who may see the charts: a `Visibility` value. */
    data class SetChartsVisibility(val visibility: String) : ActivityDetailEvent

    /** Opens the training zones screen, to set the thresholds zone charts need. */
    data object OpenTrainingZones : ActivityDetailEvent

    data class ToggleSection(val section: DetailSection) : ActivityDetailEvent

    /** Expand every section when none is expanded, else collapse them all. */
    data object ToggleAll : ActivityDetailEvent
}
