package io.github.solcott.activitytracker.app.gear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.activitylist.rememberActivityListEventSink
import io.github.solcott.activitytracker.app.moderation.rememberRevealed
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.gear.GearCollection
import io.github.solcott.activitytracker.data.gear.GearCollections
import io.github.solcott.activitytracker.data.gear.GearRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetGear
import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.LoadStatus
import io.github.solcott.uistate.circuit.produceRetainedContentState
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import io.github.solcott.uistate.hasLoaded
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * A gear item's page, read again on Retry and after every save of the athlete's own gear this session ([GearRepository.changes]), so coming
 * back from Edit shows the change once the AppView has it. On the owner's page, their collections are read from their `self` space, again
 * after every change to them, and a toggle saves the item's membership at once ([GearCollections.setMembership]).
 */
@CircuitInject(GearScreen::class, SessionScope::class)
@Composable
fun GearPresenter(
    screen: GearScreen,
    navigator: Navigator,
    gear: GearRepository,
    collections: GearCollections,
    activities: ActivityRepository,
): GearState {
    val uri = AtUri(screen.uri)
    val isOwner = uri.authority == gear.did.value
    var loadKey by remember { mutableIntStateOf(0) }
    val changes by gear.changes.collectAsState()
    val content =
        produceRetainedContentState<GearPage?>(null, loadKey to changes) {
            gear.usageOutcome(uri).map { outcome -> outcome.mapData { pageOf(it, isOwner) } }
        }
    val counted =
        rememberRetainedPaging(screen, changes, idOf = ActivityView::uri) { cursor -> flow { emit(gear.activitiesPage(uri, cursor)) } }
    val collectionChanges by collections.changes.collectAsState()
    val owned by
        produceState(emptyList<GearCollection>(), isOwner, collectionChanges) {
            if (isOwner) value = runSuspendCatching { collections.all() }.getOrDefault(value)
        }
    val flights = rememberSingleFlight()
    var error by rememberRetained { mutableStateOf<UiError?>(null) }
    val activityEvents = rememberActivityListEventSink(navigator, activities) { error = it }
    val failure = (content.status as? LoadStatus.Failed)?.error

    return GearState(
        content = content,
        notFound = !content.hasLoaded && failure is DataError.Api && failure.code == NOT_FOUND,
        activities = counted.state,
        viewerDid = gear.did.value,
        revealed = rememberRevealed(),
        collections = owned.map { GearCollectionChoice(it.rkey, it.record.name, uri in it.items) }.toImmutableList(),
        error = error,
    ) { event ->
        when (event) {
            GearEvent.Back -> navigator.pop()
            GearEvent.Retry -> {
                loadKey++
                counted.retry()
            }
            GearEvent.LoadMore -> counted.loadMore()
            GearEvent.Edit -> uri.rkey?.takeIf { isOwner }?.let { navigator.goTo(EditGearScreen(it)) }
            is GearEvent.ToggleCollection -> {
                error = null
                flights.launch(GearEvent.ToggleCollection::class) {
                    val now = owned.filter { uri in it.items }.map { it.rkey }.toSet()
                    val wanted = if (event.rkey in now) now - event.rkey else now + event.rkey
                    runSuspendCatching { collections.setMembership(uri, wanted) }.onFailure { error = collectionSaveErrorOf(it) }
                }
            }
            is GearEvent.Activity -> {
                error = null
                activityEvents(event.event)
            }
        }
    }
}

private fun pageOf(output: GetGear.Output, isOwner: Boolean) =
    GearPage(gear = output.gear, usage = output.usage, retired = output.retired, isOwner = isOwner, visibility = output.visibility)

private const val NOT_FOUND = "NotFound"
