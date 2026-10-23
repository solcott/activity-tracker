package io.github.solcott.activitytracker.app.gear

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.GearMainTotal
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** The signed-in athlete's gear, grouped by type, active items first and then the retired ones. */
@CircuitSerializable(SessionScope::class) data object GearListScreen : Screen

/**
 * One item as the list shows it. [type] is a known `GearType` (one this client doesn't know is `other`), and [kind] one of its kinds or
 * null. [visibility] is a `Visibility` value. [totals] are its usage totals from the AppView (gear/gear-usage), of which the row shows
 * [mainTotal]; null while unknown (not yet read, or the read failed), when the row shows none.
 */
@Immutable
data class GearRow(
    val rkey: String,
    val name: String,
    val type: String,
    val kind: String?,
    val visibility: String,
    val photoUrl: String?,
    val mainTotal: GearMainTotal = GearType.mainTotal(type),
    val totals: GearTotals? = null,
)

/** The items of one [type], in name order. */
@Immutable data class GearGroup(val type: String, val items: ImmutableList<GearRow>)

/** The athlete's gear: [active] items grouped by type in picker order, then every [retired] one. */
@Immutable
data class GearLists(val active: ImmutableList<GearGroup> = persistentListOf(), val retired: ImmutableList<GearRow> = persistentListOf()) {
    val isEmpty: Boolean
        get() = active.isEmpty() && retired.isEmpty()
}

@Immutable data class GearListState(val content: ContentState<GearLists>, val eventSink: (GearListEvent) -> Unit) : CircuitUiState

sealed interface GearListEvent {
    data object Back : GearListEvent

    data object Retry : GearListEvent

    data object Add : GearListEvent

    data class Open(val rkey: String) : GearListEvent
}
