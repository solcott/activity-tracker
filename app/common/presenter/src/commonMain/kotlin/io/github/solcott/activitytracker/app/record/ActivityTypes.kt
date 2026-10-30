package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Saves an [ActivityType] with `rememberSaveable`. */
val ActivityTypeSaver: Saver<ActivityType, Any> =
    listSaver(save = { listOf(it.sport, it.subSport) }, restore = { ActivityType(requireNotNull(it[0]), it[1]) })

/** How many types the picker's quick row offers. */
private const val QUICK_TYPES = 8

/** The picker's quick row: the athlete's recent types, then the common ones. [outdoorOnly] leaves out types that can't use GPS. */
@Composable
fun quickActivityTypes(recent: RecentActivityTypes, outdoorOnly: Boolean = false): ImmutableList<ActivityType> {
    val recentTypes by recent.types.collectAsState(emptyList())
    return remember(recentTypes, outdoorOnly) {
        (recentTypes + SportCatalog.common)
            .distinct()
            .filterNot { outdoorOnly && SportCatalog.isIndoor(it) }
            .take(QUICK_TYPES)
            .toImmutableList()
    }
}
