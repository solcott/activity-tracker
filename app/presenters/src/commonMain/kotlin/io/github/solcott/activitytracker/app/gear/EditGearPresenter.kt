package io.github.solcott.activitytracker.app.gear

import androidx.compose.foundation.text.input.TextFieldState
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
import io.github.solcott.activitytracker.app.profile.rememberApprovesFollowers
import io.github.solcott.activitytracker.app.rememberFormSeed
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.activity.NewPhoto
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.gear.GearItem
import io.github.solcott.activitytracker.data.gear.GearNeedsSignInException
import io.github.solcott.activitytracker.data.gear.GearPhotoEdit
import io.github.solcott.activitytracker.data.gear.GearRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.github.solcott.dataresult.Origin
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.LoadStatus
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

private val TYPES: ImmutableList<String> = GearType.all.toImmutableList()

/**
 * Adds or edits a gear item. Every save, retire and delete is a direct call ([GearRepository]) that goes back to the list when it's done,
 * and keeps the form as filled in when it fails. A new item starts at Only me.
 */
@CircuitInject(EditGearScreen::class, SessionScope::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod") // One form, and one branch per event.
fun EditGearPresenter(
    screen: EditGearScreen,
    navigator: Navigator,
    gear: GearRepository,
    profiles: ProfileRepository,
    clock: Clock,
): EditGearState {
    val rkey = screen.rkey
    var loadKey by remember { mutableIntStateOf(0) }
    val loaded = produceRetainedContentState<GearItem?>(null, loadKey) { rkey?.let(gear::itemOutcome) ?: emptyFlow() }
    var existing by rememberRetained { mutableStateOf<GearItem?>(null) }
    val name = rememberTextFieldState()
    val brand = rememberTextFieldState()
    val model = rememberTextFieldState()
    val weight = rememberTextFieldState()
    val notes = rememberTextFieldState()
    var type by rememberRetained { mutableStateOf(GearType.BIKE) }
    // As stored, so a kind this client doesn't know survives a save unless the type changes.
    var kind by rememberRetained { mutableStateOf<String?>(null) }
    var sports by rememberRetained { mutableStateOf(emptyList<String>()) }
    var visibility by rememberRetained { mutableStateOf(Visibility.SELF) }
    var acquiredOn by rememberRetained { mutableStateOf<LocalDate?>(null) }
    var retiredAt by rememberRetained { mutableStateOf<Instant?>(null) }
    var photo by rememberRetained { mutableStateOf<GearPhoto?>(null) }
    var photoEdit by rememberRetained { mutableStateOf<GearPhotoEdit>(GearPhotoEdit.Keep) }
    var nameMissing by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    val approvesFollowers = rememberApprovesFollowers(profiles)

    val seed =
        rememberFormSeed(loaded) { item ->
            val record = item.record
            existing = item
            name.setTextAndPlaceCursorAtEnd(record.name)
            brand.setTextAndPlaceCursorAtEnd(record.brand.orEmpty())
            model.setTextAndPlaceCursorAtEnd(record.model.orEmpty())
            weight.setTextAndPlaceCursorAtEnd(record.weight?.toString().orEmpty())
            notes.setTextAndPlaceCursorAtEnd(record.notes.orEmpty())
            type = record.type
            kind = record.kind
            sports = record.sports.orEmpty()
            visibility = item.visibility
            acquiredOn = record.acquiredAt?.toLocalDateTime(TimeZone.UTC)?.date
            retiredAt = record.retiredAt
            photo = item.photoUrl?.let(GearPhoto::Saved)
        }

    fun save(retired: Instant?) {
        val trimmed = name.text.trim().toString()
        if (trimmed.isEmpty()) {
            nameMissing = true
            return
        }
        nameMissing = false
        error = null
        flights.launch(SAVE) {
            val base = existing?.record ?: GearItemRecord(trimmed, type, clock.now())
            val record =
                base.copy(
                    name = trimmed,
                    type = type,
                    kind = kind,
                    brand = brand.trimmedOrNull(),
                    model = model.trimmedOrNull(),
                    sports = sports.ifEmpty { null },
                    weight = weight.text.toString().toIntOrNull()?.coerceIn(0, GearItemRecord.MAX_WEIGHT),
                    notes = notes.trimmedOrNull(),
                    acquiredAt = acquiredOn?.atStartOfDayIn(TimeZone.UTC),
                    retiredAt = retired,
                )
            runSuspendCatching { gear.save(existing, record, visibility, photoEdit) }
                .onSuccess { navigator.pop() }
                .onFailure { error = saveErrorOf(it) }
        }
    }

    val content = if (rkey == null) ContentState(Unit, Origin.Network, LoadStatus.Idle) else loaded.formContent(seeded = seed != null)
    return EditGearState(
        content = content,
        isNew = rkey == null,
        name = name,
        brand = brand,
        model = model,
        weight = weight,
        notes = notes,
        type = type,
        types = TYPES,
        kind = GearType.knownKind(type, kind),
        kinds = GearType.kinds(type).toImmutableList(),
        sports = sports.toImmutableList(),
        visibility = visibility,
        acquiredOn = acquiredOn,
        retiredOn = retiredAt?.toLocalDateTime(TimeZone.currentSystemDefault())?.date,
        photo = photo,
        nameMissing = nameMissing && name.text.isBlank(),
        saving = flights.isRunning(SAVE) || flights.isRunning(DELETE),
        confirmingDelete = confirmingDelete,
        approvesFollowers = approvesFollowers,
        error = error,
    ) { event ->
        when (event) {
            EditGearEvent.Back -> navigator.pop()
            EditGearEvent.Retry -> loadKey++
            is EditGearEvent.TypeChanged -> {
                if (event.type != type) kind = null
                type = event.type
            }
            is EditGearEvent.KindChanged -> kind = event.kind
            is EditGearEvent.ToggleSport ->
                sports = if (event.sport in sports) sports - event.sport else (sports + event.sport).take(GearItemRecord.MAX_SPORTS)
            is EditGearEvent.VisibilityChanged -> visibility = event.visibility
            is EditGearEvent.AcquiredChanged -> acquiredOn = event.date
            is EditGearEvent.PhotoPicked -> {
                photo = GearPhoto.Picked(event.photo)
                photoEdit = GearPhotoEdit.Replace(event.photo.let { NewPhoto(it.bytes.toByteArray(), it.mimeType, it.width, it.height) })
            }
            EditGearEvent.RemovePhoto -> {
                photo = null
                photoEdit = GearPhotoEdit.Remove
            }
            EditGearEvent.Save -> save(retiredAt)
            EditGearEvent.Retire -> {
                confirmingDelete = false
                save(clock.now())
            }
            EditGearEvent.Unretire -> save(null)
            EditGearEvent.Delete -> confirmingDelete = true
            EditGearEvent.DismissDelete -> confirmingDelete = false
            EditGearEvent.ConfirmDelete -> {
                confirmingDelete = false
                val item = existing ?: return@EditGearState
                error = null
                flights.launch(DELETE) {
                    runSuspendCatching { gear.delete(item) }
                        .onSuccess { navigator.pop() }
                        .onFailure { error = UiError.of(UiError.Kind.DeleteGear, it) }
                }
            }
        }
    }
}

private const val SAVE = "save"
private const val DELETE = "delete"

private fun TextFieldState.trimmedOrNull(): String? = text.trim().toString().ifEmpty { null }

/** A session whose grant predates gear asks to sign in again, whether this client or the PDS refused the save. */
private fun saveErrorOf(e: Throwable): UiError =
    when {
        e is GearNeedsSignInException -> UiError(UiError.Kind.GearSignInAgain)
        e is XrpcException && e.error == SpaceErrors.SCOPE_MISSING -> UiError(UiError.Kind.GearSignInAgain)
        else -> UiError.of(UiError.Kind.SaveGear, e)
    }
