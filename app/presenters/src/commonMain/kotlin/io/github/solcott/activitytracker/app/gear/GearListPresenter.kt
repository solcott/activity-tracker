package io.github.solcott.activitytracker.app.gear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.gear.GearCollection
import io.github.solcott.activitytracker.data.gear.GearCollections
import io.github.solcott.activitytracker.data.gear.GearItem
import io.github.solcott.activitytracker.data.gear.GearRepository
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map

/**
 * The athlete's gear and their collections, read again after every save or delete of either this session ([GearRepository.changes],
 * [GearCollections.changes]) and on Retry. The items come from their own repo and spaces, so a new one shows at once; each one's totals
 * come from the AppView (gear/gear-usage), and a failed read of those only leaves the totals out.
 */
@CircuitInject(GearListScreen::class, SessionScope::class)
@Composable
fun GearListPresenter(navigator: Navigator, gear: GearRepository, collections: GearCollections): GearListState {
    var retryKey by remember { mutableIntStateOf(0) }
    val changes by gear.changes.collectAsState()
    val collectionChanges by collections.changes.collectAsState()
    var totals by rememberRetained { mutableStateOf<Map<String, GearTotals>?>(null) }
    LaunchedEffect(retryKey, changes) {
        runSuspendCatching { gear.actorGear(gear.did.value) }
            .onSuccess { items -> totals = items.associate { it.gear.uri.value to it.totals } }
    }
    val content =
        produceRetainedContentState(LoadedGear(), Triple(retryKey, changes, collectionChanges)) {
            gear.gearAndCollectionsOutcome().map { outcome -> outcome.mapData { LoadedGear(it.items, it.collections) } }
        }
    val lists = remember(content, totals) { content.map { listsOf(it.items, gear.did, totals, it.collections) } }
    return GearListState(lists) { event ->
        when (event) {
            GearListEvent.Back -> navigator.pop()
            GearListEvent.Retry -> retryKey++
            GearListEvent.Add -> navigator.goTo(EditGearScreen())
            is GearListEvent.Open -> navigator.goTo(GearScreen(GearItem.uriOf(gear.did, event.rkey).value))
            is GearListEvent.OpenCollection -> navigator.goTo(GearCollectionScreen(event.rkey))
            GearListEvent.AddCollection -> navigator.goTo(EditGearCollectionScreen())
        }
    }
}

/** The items and collections as read, wrapped so an empty first answer differs from the placeholder before it only by its load status. */
private data class LoadedGear(val items: List<GearItem> = emptyList(), val collections: List<GearCollection> = emptyList())

private fun <T, R> ContentState<T>.map(transform: (T) -> R): ContentState<R> = ContentState(transform(data), origin, status)

/**
 * [items] as the list shows them: [collections] by name, active items grouped by type in picker order (unknown types under Other), then the
 * retired ones. Each row's totals are the AppView's ([totals], by canonical URI, null while unknown); an item it doesn't list yet, just
 * added, has none counted beyond its own starting usage.
 */
internal fun listsOf(
    items: List<GearItem>,
    did: Did,
    totals: Map<String, GearTotals>?,
    collections: List<GearCollection> = emptyList(),
): GearLists {
    val rows = items.sortedBy { it.record.name.lowercase() }
    val (retired, active) = rows.partition { it.record.retiredAt != null }
    val groups = active.groupBy { GearType.known(it.record.type) }
    val row = { item: GearItem -> rowOf(item, totals?.let { it[item.uri(did).value] ?: startingOf(item) }) }
    return GearLists(
        active = GearType.all.mapNotNull { type -> groups[type]?.let { GearGroup(type, it.map(row).toImmutableList()) } }.toImmutableList(),
        retired = retired.map(row).toImmutableList(),
        collections = collections.map { GearCollectionRow(it.rkey, it.record.name, it.items.size) }.toImmutableList(),
    )
}

private fun startingOf(item: GearItem): GearTotals =
    GearTotals.ZERO.copy(distance = item.record.startingDistance ?: 0, movingTime = item.record.startingTime ?: 0)

/** [item] as a list shows it, with [totals] when known. */
internal fun rowOf(item: GearItem, totals: GearTotals? = null): GearRow {
    val type = GearType.known(item.record.type)
    return GearRow(
        rkey = item.rkey,
        name = item.record.name,
        type = type,
        kind = GearType.knownKind(type, item.record.kind),
        visibility = item.visibility,
        photoUrl = item.photoUrl,
        mainTotal = GearType.mainTotal(item.record.type),
        totals = totals,
        retired = item.record.retiredAt != null,
    )
}
