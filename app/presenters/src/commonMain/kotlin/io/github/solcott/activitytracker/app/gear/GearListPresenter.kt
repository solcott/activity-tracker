package io.github.solcott.activitytracker.app.gear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.gear.GearItem
import io.github.solcott.activitytracker.data.gear.GearRepository
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map

/** The athlete's gear, read again after every save or delete this session ([GearRepository.changes]) and on Retry. */
@CircuitInject(GearListScreen::class, SessionScope::class)
@Composable
fun GearListPresenter(navigator: Navigator, gear: GearRepository): GearListState {
    var retryKey by remember { mutableIntStateOf(0) }
    val changes by gear.changes.collectAsState()
    val content =
        produceRetainedContentState(GearLists(), retryKey to changes) { gear.itemsOutcome().map { outcome -> outcome.mapData(::listsOf) } }
    return GearListState(content) { event ->
        when (event) {
            GearListEvent.Back -> navigator.pop()
            GearListEvent.Retry -> retryKey++
            GearListEvent.Add -> navigator.goTo(EditGearScreen())
            is GearListEvent.Open -> navigator.goTo(EditGearScreen(event.rkey))
        }
    }
}

/** [items] as the list shows them: active ones grouped by type in picker order (unknown types under Other), then the retired ones. */
internal fun listsOf(items: List<GearItem>): GearLists {
    val rows = items.sortedBy { it.record.name.lowercase() }
    val (retired, active) = rows.partition { it.record.retiredAt != null }
    val groups = active.groupBy { GearType.known(it.record.type) }
    return GearLists(
        active =
            GearType.all.mapNotNull { type -> groups[type]?.let { GearGroup(type, it.map(::rowOf).toImmutableList()) } }.toImmutableList(),
        retired = retired.map(::rowOf).toImmutableList(),
    )
}

private fun rowOf(item: GearItem): GearRow {
    val type = GearType.known(item.record.type)
    return GearRow(
        rkey = item.rkey,
        name = item.record.name,
        type = type,
        kind = GearType.knownKind(type, item.record.kind),
        visibility = item.visibility,
        photoUrl = item.photoUrl,
    )
}
