package io.github.solcott.activitytracker.app.gear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.gear.GearCollectionItems
import io.github.solcott.activitytracker.data.gear.GearCollections
import io.github.solcott.activitytracker.data.gear.GearItem
import io.github.solcott.activitytracker.data.gear.GearRepository
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map

/**
 * One of the athlete's gear collections, read again after every save or delete of gear or collections this session and on Retry. Members
 * open their gear page; deleting goes back once it's done, and leaves the items and every activity's gear as they are.
 */
@CircuitInject(GearCollectionScreen::class, SessionScope::class)
@Composable
fun GearCollectionPresenter(
    screen: GearCollectionScreen,
    navigator: Navigator,
    gear: GearRepository,
    collections: GearCollections,
): GearCollectionState {
    var retryKey by remember { mutableIntStateOf(0) }
    val changes by gear.changes.collectAsState()
    val collectionChanges by collections.changes.collectAsState()
    val content =
        produceRetainedContentState(GearCollectionDetail(), Triple(retryKey, changes, collectionChanges)) {
            gear.collectionOutcome(screen.rkey).map { outcome -> outcome.mapData(::detailOf) }
        }
    var confirmingDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    return GearCollectionState(
        content = content,
        confirmingDelete = confirmingDelete,
        deleting = flights.isRunning(GearCollectionEvent.ConfirmDelete),
        error = error,
    ) { event ->
        when (event) {
            GearCollectionEvent.Back -> navigator.pop()
            GearCollectionEvent.Retry -> retryKey++
            is GearCollectionEvent.Open -> navigator.goTo(GearScreen(GearItem.uriOf(gear.did, event.rkey).value))
            GearCollectionEvent.Edit -> navigator.goTo(EditGearCollectionScreen(screen.rkey))
            GearCollectionEvent.Delete -> confirmingDelete = true
            GearCollectionEvent.DismissDelete -> confirmingDelete = false
            GearCollectionEvent.ConfirmDelete -> {
                confirmingDelete = false
                error = null
                flights.launch(GearCollectionEvent.ConfirmDelete) {
                    runSuspendCatching { collections.delete(screen.rkey) }
                        .onSuccess { navigator.pop() }
                        .onFailure { error = UiError.of(UiError.Kind.DeleteGearCollection, it) }
                }
            }
        }
    }
}

private fun detailOf(read: GearCollectionItems): GearCollectionDetail =
    GearCollectionDetail(
        name = read.collection.record.name,
        notes = read.collection.record.notes,
        members = read.members.map(::rowOf).toImmutableList(),
    )
