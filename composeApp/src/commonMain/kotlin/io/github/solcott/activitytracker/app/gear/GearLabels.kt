package io.github.solcott.activitytracker.app.gear

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.composeApp.resources.gear_type_other
import io.github.solcott.activitytracker.lexicon.app.GearType
import org.jetbrains.compose.resources.stringResource

/** A `GearType` value's name (`gear_type_<token>`); Other for one this client doesn't know. */
@Composable
fun gearTypeLabel(type: String): String =
    Res.allStringResources["gear_type_${GearType.known(type)}"]?.let { stringResource(it) } ?: stringResource(Res.string.gear_type_other)

/** [kind]'s name within [type] (`gear_kind_<type>_<kind>`); the type's name for a kind this client doesn't know. */
@Composable
fun gearKindLabel(type: String, kind: String): String =
    Res.allStringResources["gear_kind_${type}_$kind"]?.let { stringResource(it) } ?: gearTypeLabel(type)
