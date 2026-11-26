package io.github.solcott.activitytracker.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.visibility.CategoryVisibility
import io.github.solcott.activitytracker.app.visibility.DetailsSummary
import io.github.solcott.activitytracker.app.visibility.DetailsSummaryLine
import io.github.solcott.activitytracker.app.visibility.VisibilityEvent
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.app.visibility.VisibilityTexts
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.detail_visibility_heading
import io.github.solcott.activitytracker.composeApp.resources.detail_visibility_help
import io.github.solcott.activitytracker.composeApp.resources.detail_visibility_match_activity
import io.github.solcott.activitytracker.composeApp.resources.detail_visibility_row
import io.github.solcott.activitytracker.composeApp.resources.detail_visibility_use_defaults
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * Who may see each detail category of an activity (or, in Settings, of new ones): one picker per offered category
 * ([VisibilityFields.categories]), each offering only the activity's visibility or narrower. Disabled, like the activity's picker, for a
 * session that can't choose. When [collapsible], as on every activity form, the pickers start folded under a row that sums them up
 * ([DetailsSummary.of]) and opens them in place; the defaults keep them open, since that is where they're set.
 */
@Composable
fun DetailVisibilityPicker(
    fields: VisibilityFields,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    collapsible: Boolean = false,
) {
    if (fields.categories.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (collapsible) {
            DetailsSummaryRow(fields, expanded, onToggle = { expanded = !expanded })
            AnimatedVisibility(expanded) { CategoryPickers(fields, enabled, Modifier.padding(top = 4.dp), resets = true) }
        } else {
            Text(stringResource(Res.string.detail_visibility_heading), style = MaterialTheme.typography.labelLarge)
            CategoryPickers(fields, enabled)
        }
    }
}

/** The help, then a picker per category. */
@Composable
private fun CategoryPickers(fields: VisibilityFields, enabled: Boolean, modifier: Modifier = Modifier, resets: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (resets) Resets(fields, enabled)
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

/** "Match the activity" and "Use my defaults", each only while it would change something. */
@Composable
private fun Resets(fields: VisibilityFields, enabled: Boolean) {
    if (!fields.offersMatchActivity && !fields.offersDefaults) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (fields.offersMatchActivity) {
            AppTextButton(onClick = { fields.eventSink(VisibilityEvent.MatchActivity) }, enabled = enabled) {
                Text(stringResource(Res.string.detail_visibility_match_activity))
            }
        }
        if (fields.offersDefaults) {
            AppTextButton(onClick = { fields.eventSink(VisibilityEvent.UseDefaults) }, enabled = enabled) {
                Text(stringResource(Res.string.detail_visibility_use_defaults))
            }
        }
    }
}

/** The folded list's row: "Details" over a line per tier, the tier set off from what it shows, and a chevron that turns as it opens. */
@Composable
private fun DetailsSummaryRow(fields: VisibilityFields, expanded: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (expanded) HALF_TURN else 0f)
    val stateText = LabelText.expanded(expanded).string()
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onToggle)
            .handCursor()
            .semantics(mergeDescendants = true) { stateDescription = stateText }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.detail_visibility_row),
                Modifier.padding(bottom = 6.dp),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (line in DetailsSummary.of(fields)) {
                    Text(summaryLine(line), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
        }
        PlatformIcon(
            AppIcon.ChevronDown,
            contentDescription = null,
            modifier = Modifier.graphicsLayer { rotationZ = turn },
            tint = colors.onSurfaceVariant,
        )
    }
}

/** [line] with its tier set semibold, in the line's own muted colour, so the row's title stays the strongest text. */
@Composable
private fun summaryLine(line: DetailsSummaryLine): AnnotatedString {
    val text = line.text.string()
    val tier = line.tier.string()
    val start = text.indexOf(tier)
    return buildAnnotatedString {
        append(text)
        if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), start, start + tier.length)
    }
}

private const val HALF_TURN = 180f

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
