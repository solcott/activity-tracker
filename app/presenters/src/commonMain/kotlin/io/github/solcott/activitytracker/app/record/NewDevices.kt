package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateMap
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.gear.GearAutoAssign
import io.github.solcott.activitytracker.data.gear.GearCollections
import io.github.solcott.activitytracker.data.gear.GearDefaults
import io.github.solcott.activitytracker.data.gear.GearItem
import io.github.solcott.activitytracker.data.gear.GearLinkEdit
import io.github.solcott.activitytracker.data.gear.GearRepository
import io.github.solcott.activitytracker.data.gear.PastImport
import io.github.solcott.activitytracker.data.gear.PastImportsMatcher
import io.github.solcott.activitytracker.data.gear.UnlinkedDevice
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.DeviceRole
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearMounts
import io.github.solcott.activitytracker.lexicon.app.GearProfileLink
import io.github.solcott.activitytracker.lexicon.app.GearSensorLink
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.SensorGear
import io.github.solcott.activitytracker.lexicon.app.SensorKey
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/** What a save of the new devices did: the gear the import is saved with, and the links it made. */
internal class NewDevicesSaved(
    /** The import's gear: the untouched picker re-resolved with the new links, plus each new item or what it's mounted on. */
    val gear: List<AtUri>,
    /** Each device added as gear, with its new item. */
    val added: List<Pair<SensorKey, GearItem>>,
    /** The links couldn't be written; the items were. */
    val linksFailed: Boolean,
)

/**
 * The import's new devices ([NewDevicesFields]): the devices its [GearSource]s name that the athlete hasn't linked or ignored, once each,
 * what they've answered for each, and the offer to link the activity profile. Hidden ([fields] empty) when the session can't save links.
 */
@Stable
@Suppress("LongParameterList") // The holder's states and what saving needs.
class NewDevices
internal constructor(
    private val canSave: () -> Boolean,
    private val gear: GearChoice,
    private val sources: () -> List<GearSource>,
    private val repository: GearRepository,
    private val collections: GearCollections,
    private val defaults: GearDefaults,
    private val matcher: PastImportsMatcher,
    private val clock: Clock,
    private val edits: SnapshotStateMap<String, CardEdit>,
    private val profileChoice: MutableState<AtUri?>,
) {
    // Each card's name field, kept for the screen: a recomposition must not reset what the athlete typed.
    private val names = mutableMapOf<String, TextFieldState>()

    // Items already saved for a card, and what's done to them, so a save that failed part-way doesn't add them twice.
    private val created = mutableMapOf<String, GearItem>()
    private val mounted = mutableSetOf<String>()
    private val joined = mutableSetOf<String>()

    /** The earlier imports offered after the last save, to add the new gear to. */
    internal var pastImports: List<PastImport> = emptyList()
        private set

    /** The devices to ask about, in file order, recording device first, each once. */
    private val unlinked: List<UnlinkedDevice>
        get() {
            val record = gear.rulesNow ?: return emptyList()
            if (!canSave()) return emptyList()
            val devices = sources().flatMap { it.devices }.sortedByDescending { it.role == DeviceRole.RECORDER }
            return GearAutoAssign.unlinked(devices, record)
        }

    /** What the screen shows and sends answers to. */
    val fields: NewDevicesFields
        get() {
            val options = gear.field.options.filter { !it.retired }.toImmutableList()
            return NewDevicesFields(
                cards = unlinked.map(::cardOf).toImmutableList(),
                mountOptions = options,
                collections = gear.field.collections,
                profileOffer = profileOffer(),
                eventSink = ::onEvent,
            )
        }

    private fun cardOf(device: UnlinkedDevice): NewDeviceCard {
        val id = idOf(device.key)
        val edit = edits[id] ?: CardEdit.of(device)
        val name = names.getOrPut(id) { TextFieldState(defaultName(device)) }
        return NewDeviceCard(
            id = id,
            manufacturer = device.device.manufacturer,
            model = device.device.model,
            recorder = device.device.role == DeviceRole.RECORDER,
            sensorKind = device.device.kind,
            serialTail = device.key.serialNumber?.takeLast(SERIAL_TAIL),
            weak = device.key.weak,
            decision = edit.decision,
            name = name,
            type = edit.type,
            kind = edit.kind,
            mountOn = mountOf(edit),
            collections = edit.collections.toImmutableSet(),
            expanded = edit.expanded,
        )
    }

    /** Where a new item is mounted: where the athlete chose, else a component goes on the bike picked for the activity. */
    private fun mountOf(edit: CardEdit): AtUri? =
        if (edit.mountTouched) edit.mountOn
        else if (edit.type != GearType.COMPONENT) null
        else gear.field.options.firstOrNull { it.uri in gear.selected && it.type == GearType.BIKE }?.uri

    /** Offered when the session can save links, the file names an unlinked profile and gear is picked. */
    private fun profileOffer(): ProfileLinkOffer? {
        val record = gear.rulesNow?.takeIf { canSave() }
        // The file's profile, unless it's linked already.
        val name =
            sources().firstNotNullOfOrNull { it.profile }?.takeIf { profile -> record?.profiles.orEmpty().none { it.name == profile } }
        val picked = gear.field.options.filter { it.uri in gear.selected }
        return if (record == null || name == null || picked.isEmpty()) null
        else ProfileLinkOffer(name, picked.toImmutableList(), profileChoice.value?.takeIf { chosen -> picked.any { it.uri == chosen } })
    }

    fun onEvent(event: NewDeviceEvent) {
        val device = event.cardId?.let { id -> unlinked.firstOrNull { idOf(it.key) == id } }
        fun update(change: (CardEdit) -> CardEdit) {
            device ?: return
            val id = idOf(device.key)
            edits[id] = change(edits[id] ?: CardEdit.of(device))
        }
        when (event) {
            is NewDeviceEvent.Decide -> update { it.copy(decision = event.decision, expanded = event.decision == NewDeviceDecision.Add) }
            is NewDeviceEvent.Expand -> update { it.copy(expanded = event.expanded) }
            is NewDeviceEvent.TypeChanged -> update { it.copy(type = event.type, kind = GearType.kinds(event.type).firstOrNull()) }
            is NewDeviceEvent.KindChanged -> update { it.copy(kind = event.kind) }
            is NewDeviceEvent.MountOn -> update { it.copy(mountOn = event.uri, mountTouched = true) }
            is NewDeviceEvent.ToggleCollection ->
                update {
                    it.copy(collections = if (event.rkey in it.collections) it.collections - event.rkey else it.collections + event.rkey)
                }
            is NewDeviceEvent.LinkProfile -> profileChoice.value = event.uri
        }
    }

    /**
     * Saves the athlete's answers before the import is published, in order: each added item (Only me), its mount from [startedAt] and its
     * collections; then the links, ignores and profile link in one write. A failure saving an item, mount or collection throws, keeping
     * what was saved for a retry; a failed link write doesn't, and is reported ([NewDevicesSaved.linksFailed]) for after the publish.
     */
    internal suspend fun save(startedAt: Instant): NewDevicesSaved {
        val answered = unlinked.map { it to (edits[idOf(it.key)] ?: CardEdit.of(it)) }
        val adding = answered.filter { it.second.decision == NewDeviceDecision.Add }
        val added = adding.map { (device, edit) ->
            val id = idOf(device.key)
            var item = created[id] ?: repository.save(null, recordOf(device, edit), Visibility.SELF).also { created[id] = it }
            val parent = mountOf(edit)
            if (parent != null && id !in mounted) {
                item = repository.mount(item, parent, startedAt)
                created[id] = item
                mounted += id
            }
            if (edit.collections.isNotEmpty() && id !in joined) {
                collections.setMembership(item.uri(repository.did), edit.collections)
                joined += id
            }
            device.key to item
        }
        val profile = sources().firstNotNullOfOrNull { it.profile }
        val edit =
            GearLinkEdit(
                link = added.map { (key, item) -> GearSensorLink(key, item.uri(repository.did)) },
                ignore = answered.filter { it.second.decision == NewDeviceDecision.NotGear }.map { it.first.key },
                linkProfiles = listOfNotNull(profileOffer()?.chosen?.let { gearUri -> profile?.let { GearProfileLink(it, gearUri) } }),
            )
        val saved = if (edit.isEmpty) null else runSuspendCatching { defaults.editLinks(edit) }.getOrNull()
        val linksFailed = !edit.isEmpty && saved == null
        // The untouched picker resolves with the links even if they weren't saved: this import was answered either way.
        val items = gear.itemsNow.filter { item -> added.none { it.second.rkey == item.rkey } } + added.map { it.second }
        gear.updated(saved ?: gear.rulesNow?.let(edit::applyTo), added.map { it.second })
        val all = items.associateBy { it.uri(repository.did) }
        val roots = added.map { (_, item) -> GearMounts.rootAt(item.uri(repository.did), startedAt) { all[it]?.record?.mounts } }
        return NewDevicesSaved((gear.selected + roots).distinct().take(ActivityRecord.MAX_GEAR), added, linksFailed)
    }

    /**
     * What to say after the import is published: that the links weren't saved, or the offer to add the new gear to the earlier imports
     * naming its devices, leaving out the activities just [saved]. Null when there's nothing to say. A failed search offers nothing.
     */
    internal suspend fun afterSave(result: NewDevicesSaved, saved: Set<AtUri>): AfterImport? {
        pastImports =
            if (result.linksFailed) emptyList()
            else
                result.added
                    .flatMap { (key, item) ->
                        runSuspendCatching { matcher.find(key, item.uri(repository.did), except = saved) }.getOrDefault(emptyList())
                    }
                    .distinctBy { it.activity }
        val names = gear.itemsNow.associate { it.uri(repository.did) to it.record.name }
        val offer =
            pastImports
                .takeIf { it.isNotEmpty() }
                ?.let { found -> PastImportsOffer(found.mapNotNull { names[it.gear] }.distinct().toImmutableList(), found.size) }
        return if (result.linksFailed || offer != null) AfterImport(linksNotSaved = result.linksFailed, pastImports = offer) else null
    }

    /** Adds the new gear to [pastImports], reporting how many are done. */
    internal suspend fun applyToPast(onProgress: (Int) -> Unit) {
        matcher.apply(pastImports, onProgress)
    }

    private fun recordOf(device: UnlinkedDevice, edit: CardEdit): GearItemRecord {
        val typed = names[idOf(device.key)]?.text?.toString()?.trim().orEmpty()
        return GearItemRecord(
            name = typed.ifEmpty { defaultName(device) }.take(GearItemRecord.MAX_NAME),
            type = edit.type,
            createdAt = clock.now(),
            kind = edit.kind,
            brand = device.device.manufacturer?.take(GearItemRecord.MAX_BRAND),
            model = device.device.model?.take(GearItemRecord.MAX_BRAND),
        )
    }

    /** What the athlete has answered for one card. */
    internal data class CardEdit(
        val type: String,
        val kind: String?,
        val decision: NewDeviceDecision = NewDeviceDecision.Later,
        val mountOn: AtUri? = null,
        val mountTouched: Boolean = false,
        val collections: Set<String> = emptySet(),
        val expanded: Boolean = false,
    ) {
        companion object {
            fun of(device: UnlinkedDevice): CardEdit {
                val suggestion = SensorGear.suggestion(device.device.role, device.device.kind)
                return CardEdit(suggestion.type, suggestion.kind)
            }
        }
    }

    private companion object {
        const val SERIAL_TAIL = 4

        fun idOf(key: SensorKey): String = key.id

        /** "{maker} {model}", or whichever the file gives; the model alone when it already starts with the maker ("Shearwater Perdix"). */
        fun defaultName(device: UnlinkedDevice): String {
            val maker = device.device.manufacturer?.trim()?.ifEmpty { null }
            val model = device.device.model?.trim()?.ifEmpty { null }
            return when {
                maker != null && model != null && model.startsWith(maker, ignoreCase = true) -> model
                else -> listOfNotNull(maker, model).joinToString(" ").ifEmpty { "Sensor" }
            }
        }
    }
}

/** The card an event is about. */
private val NewDeviceEvent.cardId: String?
    get() =
        when (this) {
            is NewDeviceEvent.Decide -> id
            is NewDeviceEvent.Expand -> id
            is NewDeviceEvent.TypeChanged -> id
            is NewDeviceEvent.KindChanged -> id
            is NewDeviceEvent.MountOn -> id
            is NewDeviceEvent.ToggleCollection -> id
            is NewDeviceEvent.LinkProfile -> null
        }

/** Produces an import's [NewDevices] for its [GearChoice] and [GearSource]s. Injected into the import presenter, like [GearChoices]. */
@Inject
class NewDeviceChoices(
    private val repository: GearRepository,
    private val collections: GearCollections,
    private val defaults: GearDefaults,
    private val matcher: PastImportsMatcher,
    private val clock: Clock,
) {
    @Composable
    fun produce(gear: GearChoice, sources: ImmutableList<GearSource>): NewDevices {
        val currentSources by rememberUpdatedState(sources)
        val edits = remember { mutableStateMapOf<String, NewDevices.CardEdit>() }
        val profileChoice = remember { mutableStateOf<AtUri?>(null) }
        return remember(gear) {
            NewDevices(
                { defaults.canSave },
                gear,
                { currentSources },
                repository,
                collections,
                defaults,
                matcher,
                clock,
                edits,
                profileChoice,
            )
        }
    }
}
