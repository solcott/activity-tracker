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
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.visibility.CategoryVisibility
import io.github.solcott.activitytracker.app.visibility.VisibilityEvent
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.app.visibility.VisibilityTexts
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.detail_visibility_heading
import io.github.solcott.activitytracker.composeApp.resources.detail_visibility_help
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * Who may see each detail category of an activity (or, in Settings, of new ones): one picker per offered category
 * ([VisibilityFields.categories]), each offering only the activity's visibility or narrower. Disabled, like the activity's picker, for a
 * session that can't choose.
 */
@Composable
fun DetailVisibilityPicker(
    fields: VisibilityFields,
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
                Text(VisibilityTexts.categoryName(fields, category.category).string(), style = MaterialTheme.typography.bodyMedium)
                VisibilityTexts.categoryHelp(category.category)?.let {
                    Text(it.string(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                VisibilityPicker(
                    category.visibility,
                    onSelect = { fields.eventSink(VisibilityEvent.CategoryChanged(category.category, it)) },
                    approvesFollowers = fields.approvesFollowers,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled && fields.canChoose,
                    options = fields.categoryOptions,
                )
            }
        }
    }
}

/** A detail category's name, by its `DetailCategory` token; an unknown token shows as it is (`LabelText.detailCategory`). */
@Composable fun detailCategoryLabel(category: String): String = LabelText.detailCategory(category).string()

/**
 * On the owner's own activity, a lock and the tier of a detail [category] stored narrower than the activity ([visibility]), shown next to
 * its stats or charts. Nothing for a public tier.
 */
@Composable
fun CategoryLock(category: String, visibility: String, modifier: Modifier = Modifier) {
    val line = VisibilityTexts.lock(category, visibility)?.string() ?: return
    val description = VisibilityTexts.lockDescription(category, visibility)?.string().orEmpty()
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformIcon(AppIcon.Lock, contentDescription = null, size = 12.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(line, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
