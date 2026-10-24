package io.github.solcott.activitytracker.app.gear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.solcott.activitytracker.activityfile.TimeZones
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.activitylist.rememberActivityListEventSink
import io.github.solcott.activitytracker.app.moderation.rememberRevealed
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.gear.GearCollection
import io.github.solcott.activitytracker.data.gear.GearCollections
import io.github.solcott.activitytracker.data.gear.GearDefaults
import io.github.solcott.activitytracker.data.gear.GearItem
import io.github.solcott.activitytracker.data.gear.GearLinkEdit
import io.github.solcott.activitytracker.data.gear.GearMountException
import io.github.solcott.activitytracker.data.gear.GearNeedsSignInException
import io.github.solcott.activitytracker.data.gear.GearNotFoundException
import io.github.solcott.activitytracker.data.gear.GearRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GearComponent
import io.github.solcott.activitytracker.lexicon.app.GearMount
import io.github.solcott.activitytracker.lexicon.app.GearMounts
import io.github.solcott.activitytracker.lexicon.app.GetGear
import io.github.solcott.activitytracker.lexicon.app.MountGraph
import io.github.solcott.activitytracker.lexicon.app.MountProblem
import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.LoadStatus
import io.github.solcott.uistate.circuit.produceRetainedContentState
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import io.github.solcott.uistate.hasLoaded
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.todayIn

/**
 * A gear item's page, read again on Retry and after every save of the athlete's own gear this session ([GearRepository.changes]), so coming
 * back from Edit shows the change once the AppView has it. On the owner's page, their collections are read from their `self` space, again
 * after every change to them, and a toggle saves the item's membership at once ([GearCollections.setMembership]). The owner also mounts,
 * unmounts and replaces components (gear/gear-components): each a direct save of the component's own record, mounts from the start of today
 * in the device's zone and unmounts at the start of the chosen day, so a part swapped on a date counts that day's rides for the new one.
 */
@CircuitInject(GearScreen::class, SessionScope::class)
@Composable
@Suppress("CyclomaticComplexMethod") // One branch per event.
fun GearPresenter(
    screen: GearScreen,
    navigator: Navigator,
    gear: GearRepository,
    collections: GearCollections,
    defaults: GearDefaults,
    activities: ActivityRepository,
    clock: Clock,
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
    // The athlete's sensor and profile links, kept by GearDefaults as they're saved.
    val rules by defaults.current.collectAsState()
    LaunchedEffect(isOwner) { if (isOwner) runSuspendCatching { defaults.load() } }
    val links = rules?.sensors.orEmpty().filter { isOwner && it.gear == uri }
    val zone = remember { TimeZones.device() }
    val ownItems by
        produceState(emptyList<GearItem>(), isOwner, changes) {
            if (isOwner) value = runSuspendCatching { gear.items() }.getOrDefault(value)
        }
    // Whether the picker is open, and the component it replaces; its candidates follow the items as they load.
    var picking by rememberRetained { mutableStateOf(false) }
    var replacing by rememberRetained { mutableStateOf<GearComponent?>(null) }
    var unmounting by rememberRetained { mutableStateOf<GearComponent?>(null) }
    val flights = rememberSingleFlight()
    var error by rememberRetained { mutableStateOf<UiError?>(null) }

    fun today(): Instant = clock.todayIn(zone).atStartOfDayIn(zone)

    fun ownItem(at: AtUri): GearItem = ownItems.firstOrNull { it.rkey == at.rkey } ?: throw GearNotFoundException()

    fun saveMount(save: suspend () -> Unit) {
        error = null
        flights.launch(MOUNT) {
            runSuspendCatching { save() }
                .onSuccess {
                    picking = false
                    unmounting = null
                }
                .onFailure { error = mountErrorOf(it) }
        }
    }

    val activityEvents = rememberActivityListEventSink(navigator, activities) { error = it }
    val failure = (content.status as? LoadStatus.Failed)?.error

    return GearState(
        content = content,
        notFound = !content.hasLoaded && failure is DataError.Api && failure.code == NOT_FOUND,
        activities = counted.state,
        viewerDid = gear.did.value,
        revealed = rememberRevealed(),
        collections = owned.map { GearCollectionChoice(it.rkey, it.record.name, uri in it.items) }.toImmutableList(),
        picker = if (picking) GearMountPicker(candidatesFor(uri, gear.did, ownItems, today()), replacing) else null,
        unmounting = unmounting,
        mountSaving = flights.isRunning(MOUNT),
        linkedSensors =
            links
                .map { LinkedSensor(it.key.id, it.key.manufacturer, it.key.model, it.key.serialNumber?.takeLast(SERIAL_TAIL), it.key.weak) }
                .toImmutableList(),
        linkedProfiles = rules?.profiles.orEmpty().filter { isOwner && it.gear == uri }.map { it.name }.toImmutableList(),
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
            is GearEvent.OpenGear -> navigator.goTo(GearScreen(event.uri))
            GearEvent.Mount -> {
                replacing = null
                picking = isOwner
            }
            is GearEvent.Replace -> {
                replacing = content.data?.components?.firstOrNull { it.gear.uri.value == event.uri }
                picking = isOwner && replacing != null
            }
            is GearEvent.ChooseMount -> {
                val chosen = ownItems.firstOrNull { it.rkey == event.rkey } ?: return@GearState
                val old = replacing
                saveMount { if (old == null) gear.mount(chosen, uri, today()) else gear.replace(ownItem(old.gear.uri), chosen, today()) }
            }
            GearEvent.MountNew -> {
                navigator.goTo(EditGearScreen(mountOn = screen.uri, replacing = replacing?.gear?.uri?.value))
                picking = false
            }
            GearEvent.DismissMount -> picking = false
            is GearEvent.Unmount -> unmounting = content.data?.components?.firstOrNull { it.gear.uri.value == event.uri }
            is GearEvent.ConfirmUnmount -> {
                val component = unmounting ?: return@GearState
                saveMount { gear.unmount(ownItem(component.gear.uri), event.date.atStartOfDayIn(zone)) }
            }
            GearEvent.DismissUnmount -> unmounting = null
            is GearEvent.UnlinkSensor -> {
                val key = links.firstOrNull { it.key.id == event.id }?.key ?: return@GearState
                error = null
                flights.launch(event) {
                    runSuspendCatching { defaults.editLinks(GearLinkEdit(unlink = listOf(key))) }
                        .onFailure { error = uiErrorOf(UiError.Kind.SaveGearLinks, it) }
                }
            }
            is GearEvent.UnlinkProfile -> {
                error = null
                flights.launch(event) {
                    runSuspendCatching { defaults.editLinks(GearLinkEdit(unlinkProfiles = setOf(event.name))) }
                        .onFailure { error = uiErrorOf(UiError.Kind.SaveGearLinks, it) }
                }
            }
            is GearEvent.Activity -> {
                error = null
                activityEvents(event.event)
            }
        }
    }
}

private fun pageOf(output: GetGear.Output, isOwner: Boolean): GearPage {
    val (current, previous) = output.components.orEmpty().partition { it.to == null }
    return GearPage(
        gear = output.gear,
        usage = output.usage,
        retired = output.retired,
        isOwner = isOwner,
        visibility = output.visibility,
        components = current.toImmutableList(),
        previous = previous.toImmutableList(),
        mounts = output.mounts.orEmpty().toImmutableList(),
    )
}

/**
 * The athlete's [items] that can be mounted on the item at [parent] from [at]: in use, not mounted anywhere now, and allowed by
 * [GearMounts] (so not the item itself or one it's mounted on), by name.
 */
private fun candidatesFor(parent: AtUri, did: Did, items: List<GearItem>, at: Instant): ImmutableList<GearMountCandidate> {
    val graph = MountGraph.of(items.associate { it.uri(did) to it.record.mounts.orEmpty() })
    return items
        .filter { item ->
            val mounts = item.record.mounts.orEmpty()
            item.uri(did) != parent &&
                item.record.retiredAt == null &&
                GearMounts.open(mounts) == null &&
                GearMounts.problem(item.uri(did), mounts + GearMount(parent, at), graph) == null
        }
        .sortedBy { it.record.name.lowercase() }
        .map { GearMountCandidate(it.rkey, it.record.name, it.record.type, it.record.kind) }
        .toImmutableList()
}

/** Why a mount, unmount or replace failed, naming the rule [GearMounts] refused it for. */
internal fun mountErrorOf(e: Throwable): UiError =
    when (e) {
        is GearMountException ->
            when (e.problem) {
                MountProblem.OVERLAP -> UiError(UiError.Kind.MountOverlap)
                MountProblem.LOOP -> UiError(UiError.Kind.MountLoop)
                MountProblem.TOO_DEEP -> UiError(UiError.Kind.MountTooDeep)
                MountProblem.EMPTY -> UiError(UiError.Kind.MountEndsBeforeStart)
                MountProblem.FOREIGN_PARENT -> UiError(UiError.Kind.SaveMount)
            }
        is GearNeedsSignInException -> UiError(UiError.Kind.GearSignInAgain)
        else -> UiError.of(UiError.Kind.SaveMount, e)
    }

private const val MOUNT = "mount"

/** How much of a serial number the page shows. */
private const val SERIAL_TAIL = 4

private const val NOT_FOUND = "NotFound"
