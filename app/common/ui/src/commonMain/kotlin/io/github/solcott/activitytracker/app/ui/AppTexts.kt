package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allPluralStringResources
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import org.jetbrains.compose.resources.pluralStringResource
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
            if (resource != null) stringResource(resource, *args.map { it.string() }.toTypedArray()) else fallback?.string() ?: name
        }
        is AppText.Plural -> {
            val resource = Res.allPluralStringResources[name]
            if (resource != null) pluralStringResource(resource, quantity, *args.map { it.string() }.toTypedArray()) else name
        }
    }
