package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.import_time_zone
import io.github.solcott.activitytracker.composeApp.resources.import_time_zone_notice
import io.github.solcott.activitytracker.composeApp.resources.import_time_zone_option
import org.jetbrains.compose.resources.stringResource

/** Warns that the file's clock has no zone, and lets the athlete pick the one it was recorded in. */
@Composable
fun TimeZoneNotice(clock: ImportClock, onPick: (String) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.import_time_zone_notice, clock.localStart), style = MaterialTheme.typography.bodyMedium)
            TimeZoneField(clock, onPick, enabled)
        }
    }
}

/** A zone chosen by typing part of its city ("cancun", "new york") or offset ("utc+8"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeZoneField(clock: ImportClock, onPick: (String) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val selected = zoneLabel(clock.zone)
    val zones = remember(clock.zones) { clock.zones.map(::ZoneEntry) }
    val query = rememberTextFieldState(selected)
    LaunchedEffect(selected) { query.setTextAndPlaceCursorAtEnd(selected) }
    var expanded by remember { mutableStateOf(false) }
    val typed = query.text.toString()
    val matches =
        if (typed == selected) {
            zones
        } else {
            val key = searchKey(typed.trim())
            zones.filter { key in it.key }
        }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && enabled }, modifier = modifier) {
        AppTextField(
            query,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled).fillMaxWidth(),
            enabled = enabled,
            label = stringResource(Res.string.import_time_zone),
            trailingIcon = { PlatformIcon(AppIcon.DropDown, null) },
            // Typing opens the menu; setting the text from code doesn't.
            inputTransformation = InputTransformation { expanded = true },
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                query.setTextAndPlaceCursorAtEnd(selected)
            },
        ) {
            matches.forEach { (zone) ->
                val label = zoneLabel(zone)
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onPick(zone.id)
                        query.setTextAndPlaceCursorAtEnd(label)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** A zone in [TimeZoneField]'s menu, with the text the search matches folded once rather than per keystroke. */
private data class ZoneEntry(val zone: TimeZoneOption, val key: String = searchKey("${cityName(zone)} ${zone.offset}"))

/** "America/New York (UTC-04:00)". */
@Composable private fun zoneLabel(zone: TimeZoneOption) = stringResource(Res.string.import_time_zone_option, cityName(zone), zone.offset)

/** Zone ids spell spaces as underscores. */
private fun cityName(zone: TimeZoneOption) = zone.id.replace('_', ' ')

/** Folds case, and lets "utc+8" find "UTC+08:00". */
private fun searchKey(text: String): String = text.lowercase().replace("+0", "+").replace("-0", "-")
