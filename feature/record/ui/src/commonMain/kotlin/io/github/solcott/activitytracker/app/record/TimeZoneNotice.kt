package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.textfield.rememberTextFieldState
import io.github.solcott.activitytracker.textfield.setTextAndPlaceCursorAtEnd

/**
 * Warns that the file's clock has no zone, and lets the athlete pick the one it was recorded in. In a batch, [fileName] says which file.
 */
@Composable
fun TimeZoneNotice(
    clock: ImportClock,
    onPick: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    fileName: String? = null,
) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ImportActivityTexts.zoneNotice(clock, fileName).string(), style = MaterialTheme.typography.bodyMedium)
            TimeZoneField(clock, onPick, enabled)
        }
    }
}

/** A zone chosen by typing part of its city ("cancun", "new york") or offset ("utc+8"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeZoneField(clock: ImportClock, onPick: (String) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val selected = zoneLabel(clock.zone)
    val query = rememberTextFieldState(selected)
    SideEffect(selected) { query.setTextAndPlaceCursorAtEnd(selected) }
    var expanded by remember { mutableStateOf(false) }
    val typed = query.text.toString()
    val matches = remember(clock, typed, selected) { if (typed == selected) clock.zones else ImportActivityTexts.zones(clock, typed) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && enabled }, modifier = modifier) {
        AppTextField(
            query,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled).fillMaxWidth(),
            enabled = enabled,
            label = ImportActivityTexts.timeZone.string(),
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
            matches.forEach { zone ->
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

/** "America/New York (UTC-04:00)". */
@Composable private fun zoneLabel(zone: TimeZoneOption) = ImportActivityTexts.zone(zone).string()
