package io.github.solcott.activitytracker.app.gear

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.formContent
import io.github.solcott.activitytracker.app.rememberFormSeed
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.di.SpacesUnsupportedException
import io.github.solcott.activitytracker.data.gear.GearCollection
import io.github.solcott.activitytracker.data.gear.GearCollections
import io.github.solcott.activitytracker.data.gear.GearItem
import io.github.solcott.activitytracker.data.gear.GearNeedsSignInException
import io.github.solcott.activitytracker.data.gear.GearRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GearCollectionRecord
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlin.time.Clock
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map

/** What the form starts from: the collection (null for a new one), its members' rkeys in order, and every item to choose from. */
private data class Loaded(val collection: GearCollection?, val members: List<String>, val items: List<GearItem>)

/**
 * Creates or edits a gear collection. Saving is a direct call ([GearCollections.save]) that goes back when it's done and keeps the form as
 * filled in when it fails. Members of deleted items are dropped from the record on save.
 */
@CircuitInject(EditGearCollectionScreen::class, SessionScope::class)
@Composable
fun EditGearCollectionPresenter(
    screen: EditGearCollectionScreen,
    navigator: Navigator,
    gear: GearRepository,
    collections: GearCollections,
    clock: Clock,
): EditGearCollectionState {
    val rkey = screen.rkey
    var loadKey by remember { mutableIntStateOf(0) }
    val loaded =
        produceRetainedContentState<Loaded?>(null, loadKey) {
            if (rkey == null) gear.itemsOutcome().map { outcome -> outcome.mapData { Loaded(null, emptyList(), it) } }
            else
                gear.collectionOutcome(rkey).map { outcome ->
                    outcome.mapData { read -> Loaded(read.collection, read.members.map { it.rkey }, read.items) }
                }
        }
    val name = rememberTextFieldState()
    val notes = rememberTextFieldState()
    var members by rememberRetained { mutableStateOf(emptyList<String>()) }
    var nameMissing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()

    val seed =
        rememberFormSeed(loaded) { read ->
            name.setTextAndPlaceCursorAtEnd(read.collection?.record?.name.orEmpty())
            notes.setTextAndPlaceCursorAtEnd(read.collection?.record?.notes.orEmpty())
            members = read.members
        }
    val items = seed?.items.orEmpty()
    val byRkey = items.associateBy { it.rkey }

    fun save() {
        val trimmed = name.text.trim().toString()
        if (trimmed.isEmpty()) {
            nameMissing = true
            return
        }
        nameMissing = false
        error = null
        flights.launch(EditGearCollectionEvent.Save) {
            val existing = seed?.collection
            val record =
                (existing?.record ?: GearCollectionRecord(trimmed, clock.now())).copy(
                    name = trimmed,
                    notes = notes.text.trim().toString().ifEmpty { null },
                    items = members.map { AtUri.of(gear.did, Nsid.GEAR_ITEM, it) }.ifEmpty { null },
                )
            runSuspendCatching { collections.save(existing, record) }
                .onSuccess { navigator.pop() }
                .onFailure { error = collectionSaveErrorOf(it) }
        }
    }

    return EditGearCollectionState(
        content = loaded.formContent(seeded = seed != null),
        isNew = rkey == null,
        name = name,
        notes = notes,
        members = members.mapNotNull { byRkey[it] }.map(::rowOf).toImmutableList(),
        others = items.filter { it.rkey !in members }.sortedBy { it.record.name.lowercase() }.map(::rowOf).toImmutableList(),
        full = members.size >= GearCollectionRecord.MAX_ITEMS,
        nameMissing = nameMissing && name.text.isBlank(),
        saving = flights.isRunning(EditGearCollectionEvent.Save),
        error = error,
    ) { event ->
        when (event) {
            EditGearCollectionEvent.Back -> navigator.pop()
            EditGearCollectionEvent.Retry -> loadKey++
            is EditGearCollectionEvent.Add ->
                members = (members + event.rkeys.filter { it !in members && it in byRkey }).take(GearCollectionRecord.MAX_ITEMS)
            is EditGearCollectionEvent.Remove -> members = members - event.rkey
            is EditGearCollectionEvent.Move ->
                if (event.from in members.indices && event.to in members.indices) {
                    members = members.toMutableList().apply { add(event.to, removeAt(event.from)) }
                }
            EditGearCollectionEvent.Save -> save()
        }
    }
}

/** A session whose grant predates collections asks to sign in again, whether this client or the PDS refused the save. */
internal fun collectionSaveErrorOf(e: Throwable): UiError =
    when {
        e is GearNeedsSignInException -> UiError(UiError.Kind.GearSignInAgain)
        e is SpacesUnsupportedException -> UiError(UiError.Kind.SpacesUnsupported)
        e is XrpcException && e.error == SpaceErrors.SCOPE_MISSING -> UiError(UiError.Kind.GearSignInAgain)
        else -> UiError.of(UiError.Kind.SaveGearCollection, e)
    }
