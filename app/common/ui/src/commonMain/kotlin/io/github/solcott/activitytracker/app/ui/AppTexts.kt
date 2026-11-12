package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.detail.timeFormat
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allPluralStringResources
import io.github.solcott.activitytracker.composeApp.resources.allStringArrayResources
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import kotlin.time.Instant
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/**
 * This text as the shared UI's strings read it (dev/shared-display-text). Shared code chooses the string and its arguments
 * (`:app-common-api`'s `StatText`, `LabelText`, `ErrorTexts`); this only reads the resource, which needs a composition. A name with no
 * string reads as its fallback, or as the name itself, as it does in the native iOS UI.
 */
// Compose's resource readers take a string's arguments as varargs, and a choice of text holds them as a list.
@Suppress("SpreadOperator")
@Composable
fun AppText.string(): String =
    when (this) {
        is AppText.Written -> text
        is AppText.Named -> {
            val resource = Res.allStringResources[name]
            when {
                resource != null -> stringResource(resource, *args.map { it.string() }.toTypedArray())
                else -> arrayItem(name) ?: fallback?.string() ?: name
            }
        }
        is AppText.Plural -> {
            val resource = Res.allPluralStringResources[name]
            if (resource != null) pluralStringResource(resource, quantity, *args.map { it.string() }.toTypedArray()) else name
        }
        is AppText.Time -> {
            val zone = utcOffsetMinutes?.let { UtcOffset(seconds = it * SECONDS_PER_MINUTE).asTimeZone() } ?: LocalTimeZone.current
            Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(zone).format(timeFormat())
        }
    }

/**
 * The item a name like `segment_rank_ordinals_2` names: the `<string-array>` before the last `_` and the index after it, the name the iOS
 * String Catalog gives each item too. Null when no array has that name or index.
 */
@Composable
private fun arrayItem(name: String): String? {
    val split = name.lastIndexOf('_')
    if (split <= 0) return null
    val index = name.substring(split + 1).toIntOrNull() ?: return null
    val resource = Res.allStringArrayResources[name.substring(0, split)] ?: return null
    return stringArrayResource(resource).getOrNull(index)
}

/** Whether [name] is an item of a shared `<string-array>`, as [arrayItem] reads it. */
fun isStringArrayItem(name: String): Boolean {
    val split = name.lastIndexOf('_')
    return split > 0 && name.substring(split + 1).toIntOrNull() != null && name.substring(0, split) in Res.allStringArrayResources
}

private const val SECONDS_PER_MINUTE = 60
