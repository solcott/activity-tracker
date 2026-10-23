package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppCheckbox
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.app.ui.adaptive.AppSwitch
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import kotlinx.collections.immutable.persistentListOf

@PreviewTest
@PreviewComponentVariants
@Composable
private fun Buttons() {
    ComponentPreview {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppButton(onClick = {}) { Text("Save") }
            AppSecondaryButton(onClick = {}) { Text("Edit") }
            AppTextButton(onClick = {}) { Text("Retry") }
            AppIconButton(AppIcon.Settings, contentDescription = "Settings", onClick = {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(onClick = {}, enabled = false) { Text("Save") }
            AppSecondaryButton(onClick = {}, enabled = false) { Text("Edit") }
            AppIconButton(AppIcon.Add, contentDescription = "Add", onClick = {}, filled = true)
        }
    }
}

@PreviewTest
@PreviewComponentVariants
@Composable
private fun TextFields() {
    ComponentPreview {
        AppTextField(TextFieldState("Morning Ride"), label = "Title")
        AppTextField(TextFieldState(), label = "Weight", placeholder = "70", supportingText = "Kilograms")
        AppTextField(TextFieldState("0"), label = "Distance", isError = true, supportingText = "Enter a distance")
    }
}

@PreviewTest
@PreviewComponentVariants
@Composable
private fun Toggles() {
    ComponentPreview {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AppCheckbox(checked = true, onCheckedChange = {})
            AppCheckbox(checked = false, onCheckedChange = {})
            AppSwitch(checked = true, onCheckedChange = {})
            AppSwitch(checked = false, onCheckedChange = {})
        }
        AppSegmentedControl(persistentListOf("Week", "Month", "Year"), selected = 1, onSelect = {})
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppFilterChip(selected = true, onClick = {}, label = { Text("Rides") })
            AppFilterChip(selected = false, onClick = {}, label = { Text("Runs") })
        }
    }
}

@PreviewTest
@PreviewComponentVariants
@Composable
private fun CardAndTopBar() {
    ComponentPreview {
        AppTopBar("Ridge Trail", onBack = {}, subtitle = "Segment") {
            AppIconButton(AppIcon.Search, contentDescription = "Search", onClick = {})
        }
        AppCard {
            Column {
                Text("Moving time")
                Text("1:00:00")
            }
        }
    }
}
