package io.github.solcott.activitytracker.location

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * Nothing to keep alive while recording on this platform: iOS keeps background location updates running itself, and desktop and web don't
 * record.
 */
@ContributesBinding(AppScope::class) @Inject class NoRecordingForeground : RecordingForeground by RecordingForeground.None
