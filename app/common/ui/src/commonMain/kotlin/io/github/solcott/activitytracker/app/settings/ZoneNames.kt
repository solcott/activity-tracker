package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.text.ZoneNameText
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.data.training.ZoneName

/** What a computed zone is called, as shared code chooses it for the zones pages and an activity's time in zone. */
@Composable fun zoneName(name: ZoneName): String = ZoneNameText.of(name).string()
