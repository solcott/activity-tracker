package io.github.solcott.activitytracker.location

/**
 * Hears a recording's status change, so a platform can keep recording while the app isn't on screen: Android runs a location foreground
 * service while the status isn't [RecordingState.Status.Idle]. Called on the recorder's thread, in order.
 */
fun interface RecordingForeground {
    fun statusChanged(status: RecordingState.Status)

    /** Platforms with nothing to keep alive: iOS keeps its location updates in the background on its own. */
    object None : RecordingForeground {
        override fun statusChanged(status: RecordingState.Status) = Unit
    }
}
