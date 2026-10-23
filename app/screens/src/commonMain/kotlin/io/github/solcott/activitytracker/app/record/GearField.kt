package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.AtUri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The gear picker of the record, manual entry, import and edit forms: the athlete's [options], those for the activity's sport first, and
 * the items [selected] for it, in the athlete's order. Their [collections] add several items at once. Picks go to [eventSink], which the
 * gear's holder fills.
 */
@Immutable
data class GearField(
    val options: ImmutableList<GearOption> = persistentListOf(),
    val selected: ImmutableList<AtUri> = persistentListOf(),
    val collections: ImmutableList<GearCollectionOption> = persistentListOf(),
    /** How many of the last added collection's items the activity's gear limit left out; 0 for none, until the next pick. */
    val notAdded: Int = 0,
    val eventSink: (GearEvent) -> Unit = {},
)

/** One of the athlete's gear collections, with how many of its items still exist. */
@Immutable data class GearCollectionOption(val rkey: String, val name: String, val count: Int)

/** One item the picker offers: an active item, or a retired one the activity already lists. */
@Immutable
data class GearOption(
    val uri: AtUri,
    val name: String,
    /** A `GearType` value. */
    val type: String,
    val kind: String? = null,
    /** Who may see the item itself (a `Visibility` value), so the athlete knows what others will see. */
    val visibility: String,
    val retired: Boolean = false,
)

sealed interface GearEvent {
    /** Selects [uri], or removes it when it's selected. */
    data class Toggle(val uri: AtUri) : GearEvent

    /** Removes every item. */
    data object Clear : GearEvent

    /** Adds the active items of the collection at [rkey] that aren't selected yet, in its order, up to the activity's gear limit. */
    data class AddCollection(val rkey: String) : GearEvent
}
