package io.github.solcott.activitytracker.app.uploads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.LocalClock
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.data.activity.RejectedChange
import io.github.solcott.activitytracker.data.activity.UploadImport
import io.github.solcott.activitytracker.data.activity.UploadItem

// The queue's words are chosen by `UploadsTexts` and `UploadImportTexts` (dev/shared-display-text), which the native iOS page asks too;
// these give them the clock and zone the shared UI reads, which previews and tests fix.

/** An item by its title, or its sport and day; an edit says it updates the activity. */
@Composable @ReadOnlyComposable internal fun itemName(item: UploadItem): AppText = UploadsTexts.itemName(item, LocalTimeZone.current)

/** A refused change: an activity by its title, or its sport and day; anything else by its kind. */
@Composable
@ReadOnlyComposable
internal fun changeName(change: RejectedChange): AppText = UploadsTexts.changeName(change, LocalTimeZone.current)

/** "Uploads paused by the server until 3:40 PM", or "until tomorrow at"; null with no pause shown. */
@Composable
internal fun serverPaused(state: UploadsState): String? =
    UploadsTexts.serverPaused(state, LocalClock.current.now(), LocalTimeZone.current)?.string()

/** "Saved Today at 7:05 AM". */
@Composable
internal fun saved(group: UploadImport): String = UploadImportTexts.saved(group, LocalClock.current.now(), LocalTimeZone.current).string()
