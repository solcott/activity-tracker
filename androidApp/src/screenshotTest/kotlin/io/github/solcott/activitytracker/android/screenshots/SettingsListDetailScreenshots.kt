package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.nav.SettingsListDetailPreview
import io.github.solcott.activitytracker.app.settings.ActivityDefaultsSamples
import io.github.solcott.activitytracker.app.settings.ActivityDefaultsUi
import io.github.solcott.activitytracker.app.settings.PrivacyZonesSamples
import io.github.solcott.activitytracker.app.settings.PrivacyZonesUi
import io.github.solcott.activitytracker.app.settings.SettingsSamples
import io.github.solcott.activitytracker.app.settings.SettingsUi

// Settings on a wide window (ui/settings-layout): the list beside a page it opened, and beside the page shown until a row is chosen.

@PreviewTest
@Preview(name = "desktop", widthDp = 1200, heightDp = 800)
@Composable
private fun SettingsListDetailPageOpen() {
    ScreenPreview {
        SettingsListDetailPreview(
            list = { SettingsUi(SettingsSamples.default) },
            page = { PrivacyZonesUi(PrivacyZonesSamples.all.loaded()) },
        )
    }
}

@PreviewTest
@Preview(name = "desktop", widthDp = 1200, heightDp = 800)
@Composable
private fun SettingsListDetailFirstPage() {
    ScreenPreview {
        SettingsListDetailPreview(
            list = { SettingsUi(SettingsSamples.default) },
            page = { ActivityDefaultsUi(ActivityDefaultsSamples.default) },
            pageOpen = false,
        )
    }
}
