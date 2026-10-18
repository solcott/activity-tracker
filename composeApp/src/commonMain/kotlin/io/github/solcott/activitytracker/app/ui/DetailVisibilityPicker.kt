package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.visibility.CategoryVisibility
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.activity_visibility_badge_followers
import io.github.solcott.activitytracker.composeApp.resources.activity_visibility_badge_mutuals
import io.github.solcott.activitytracker.composeApp.resources.activity_visibility_badge_self
import io.github.solcott.activitytracker.composeApp.resources.detail_category_cadence
import io.github.solcott.activitytracker.composeApp.resources.detail_category_calories
import io.github.solcott.activitytracker.composeApp.resources.detail_category_dive
import io.github.solcott.activitytracker.composeApp.resources.detail_category_dive_help
import io.github.solcott.activitytracker.composeApp.resources.detail_category_heartRate
import io.github.solcott.activitytracker.composeApp.resources.detail_category_lock_description
import io.github.solcott.activitytracker.composeApp.resources.detail_category_map
import io.github.solcott.activitytracker.composeApp.resources.detail_category_physiology
import io.github.solcott.activitytracker.composeApp.resources.detail_category_physiology_help
import io.github.solcott.activitytracker.composeApp.resources.detail_category_power
import io.github.solcott.activitytracker.composeApp.resources.detail_category_speed
import io.github.solcott.activitytracker.composeApp.resources.detail_category_startTime
import io.github.solcott.activitytracker.composeApp.resources.detail_category_startTime_help
import io.github.solcott.activitytracker.composeApp.resources.detail_visibility_heading
import io.github.solcott.activitytracker.composeApp.resources.detail_visibility_help
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * Who may see each detail category of an activity (or, in Settings, of new ones): one picker per offered category
 * ([VisibilityFields.categories]), each offering only the activity's visibility or narrower. Disabled, like the activity's picker, for a
 * session that can't choose.
 */
@Composable
fun DetailVisibilityPicker(
    fields: VisibilityFields,
    onCategoryChange: (category: String, visibility: String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (fields.categories.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.detail_visibility_heading), style = MaterialTheme.typography.labelLarge)
        Text(
            stringResource(Res.string.detail_visibility_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (category in fields.categories) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(detailCategoryLabel(category.category), style = MaterialTheme.typography.bodyMedium)
                detailCategoryHelp(category.category)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                VisibilityPicker(
                    category.visibility,
                    onSelect = { onCategoryChange(category.category, it) },
                    approvesFollowers = fields.approvesFollowers,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled && fields.canChoose,
                    options = fields.categoryOptions,
                )
            }
        }
    }
}

/** A detail category's name, by its `DetailCategory` token; an unknown token shows as it is. */
@Composable
fun detailCategoryLabel(category: String): String =
    when (category) {
        DetailCategory.START_TIME -> stringResource(Res.string.detail_category_startTime)
        DetailCategory.HEART_RATE -> stringResource(Res.string.detail_category_heartRate)
        DetailCategory.POWER -> stringResource(Res.string.detail_category_power)
        DetailCategory.SPEED -> stringResource(Res.string.detail_category_speed)
        DetailCategory.CALORIES -> stringResource(Res.string.detail_category_calories)
        DetailCategory.MAP -> stringResource(Res.string.detail_category_map)
        DetailCategory.PHYSIOLOGY -> stringResource(Res.string.detail_category_physiology)
        DetailCategory.CADENCE -> stringResource(Res.string.detail_category_cadence)
        DetailCategory.DIVE -> stringResource(Res.string.detail_category_dive)
        else -> category
    }

@Composable
private fun detailCategoryHelp(category: String): String? =
    when (category) {
        DetailCategory.START_TIME -> stringResource(Res.string.detail_category_startTime_help)
        DetailCategory.PHYSIOLOGY -> stringResource(Res.string.detail_category_physiology_help)
        DetailCategory.DIVE -> stringResource(Res.string.detail_category_dive_help)
        else -> null
    }

/**
 * On the owner's own activity, a lock and the tier of a detail [category] stored narrower than the activity ([visibility]), shown next to
 * its stats or charts. Nothing for a public tier.
 */
@Composable
fun CategoryLock(category: String, visibility: String, modifier: Modifier = Modifier) {
    val tier =
        when (Visibility.known(visibility)) {
            Visibility.FOLLOWERS -> stringResource(Res.string.activity_visibility_badge_followers)
            Visibility.MUTUALS -> stringResource(Res.string.activity_visibility_badge_mutuals)
            Visibility.SELF -> stringResource(Res.string.activity_visibility_badge_self)
            else -> return
        }
    val description = stringResource(Res.string.detail_category_lock_description, detailCategoryLabel(category), tier)
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformIcon(AppIcon.Lock, contentDescription = null, size = 12.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${detailCategoryLabel(category)} · $tier",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A [CategoryLock] for each of [locked], wrapping; nothing when it's empty. */
@Composable
fun CategoryLocks(locked: ImmutableList<CategoryVisibility>, modifier: Modifier = Modifier) {
    if (locked.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        locked.forEach { CategoryLock(it.category, it.visibility) }
    }
}

/**
 * The detail categories of the owner's own [activity] whose data in its view is stored narrower than the activity, from its record and its
 * details (a feed has no streams to read channels from).
 */
fun narrowerCategories(activity: ActivityView): ImmutableList<CategoryVisibility> {
    val tier = activity.visibilityOrPublic
    return DetailCategory.currentTiers(tier, activity, null)
        .filter { (_, t) -> Visibility.rank(t) > Visibility.rank(tier) }
        .entries
        .sortedBy { DetailCategory.all.indexOf(it.key) }
        .map { CategoryVisibility(it.key, it.value) }
        .toImmutableList()
}
