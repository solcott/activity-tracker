package io.github.solcott.activitytracker.app.gear

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.settings.SettingsPage
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.GearMainTotal
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** The signed-in athlete's gear: their collections, then items grouped by type, active items first and then the retired ones. */
@CircuitSerializable(SessionScope::class) data object GearListScreen : SettingsPage

/**
 * One item as a list shows it. [type] is a known `GearType` (one this client doesn't know is `other`), and [kind] one of its kinds or null.
 * [visibility] is a `Visibility` value. [totals] are its usage totals from the AppView (gear/gear-usage), of which the row shows
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
    val retired: Boolean = false,
)

/** One of the athlete's gear collections as the list shows it: its name and how many of its items still exist. */
@Immutable data class GearCollectionRow(val rkey: String, val name: String, val count: Int)

/** The items of one [type], in name order. */
@Immutable data class GearGroup(val type: String, val items: ImmutableList<GearRow>)

/** The athlete's gear: their [collections] by name, [active] items grouped by type in picker order, then every [retired] one. */
@Immutable
data class GearLists(
    val active: ImmutableList<GearGroup> = persistentListOf(),
    val retired: ImmutableList<GearRow> = persistentListOf(),
    val collections: ImmutableList<GearCollectionRow> = persistentListOf(),
) {
    val isEmpty: Boolean
        get() = active.isEmpty() && retired.isEmpty() && collections.isEmpty()

    /** Whether the Collections section shows, with New collection: once the athlete has any gear or collection. */
    val showsCollections: Boolean
        get() = !isEmpty

    /** Whether the Collections section explains collections, in place of rows: while it shows and holds none. */
    val explainsCollections: Boolean
        get() = showsCollections && collections.isEmpty()
}

@Immutable data class GearListState(val content: ContentState<GearLists>, val eventSink: (GearListEvent) -> Unit) : CircuitUiState

sealed interface GearListEvent {
    data object Back : GearListEvent

    data object Retry : GearListEvent

    data object Add : GearListEvent

    data class Open(val rkey: String) : GearListEvent

    /** Opens the collection at [rkey]. */
    data class OpenCollection(val rkey: String) : GearListEvent

    /** Starts a new collection. */
    data object AddCollection : GearListEvent

    /** Opens the devices the athlete marked as not gear ([IgnoredSensorsScreen]). */
    data object OpenIgnoredSensors : GearListEvent
}

/** What the gear list says that follows a rule, chosen here for the shared and the native UI (dev/shared-display-text). */
object GearListTexts {
    /**
     * The line under an item's name: its kind (or its type when it has none) and who may see it, then Retired when [showRetired] and it is.
     */
    fun detail(row: GearRow, showRetired: Boolean): AppText {
        val detail =
            AppText.named(
                "gear_total_joined",
                row.kind?.let { GearTexts.kind(row.type, it) } ?: GearTexts.type(row.type),
                LabelText.visibility(row.visibility),
            )
        return if (showRetired && row.retired) AppText.named("gear_total_joined", detail, AppText.named("gear_retired")) else detail
    }

    /** A section's header: the gear [type]'s name, or Retired for the section of retired items, whose type is null. */
    fun section(type: String?): AppText = type?.let(GearTexts::type) ?: AppText.named("gear_retired")

    /** The item's one total in [units], or null while its totals are unknown, when the row shows none. */
    fun total(row: GearRow, units: UnitSystem): AppText? = row.totals?.let { GearTexts.mainTotal(row.mainTotal, it, units) }

    /** How many of a collection's items still exist: "4 items". */
    fun collectionCount(row: GearCollectionRow): AppText =
        AppText.Plural("gear_collection_count", row.count, persistentListOf(AppText.written(formatWhole(row.count))))
}
