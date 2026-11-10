package io.github.solcott.activitytracker.app

/** What a signed-in session shows (auth/leaving); see [sessionModeOf]. */
enum class SessionMode {
    /** Not yet known whether a leave is recorded on this device: nothing shows, for the moment Room takes to answer. */
    Unknown,

    /** A leave of this account is recorded on this device and hasn't finished: only its progress shows. */
    Leaving,

    /** The athlete left on another device: only the choice to discard this device's work or rejoin shows. */
    LeftElsewhere,

    /** The app. */
    Open,
}

/**
 * The session's mode from whether a leave is recorded on this device ([leaving], null until known) and whether the athlete [left] on
 * another device. A leave here wins: its own deletion of `self` reads as having left.
 */
fun sessionModeOf(leaving: Boolean?, left: Boolean): SessionMode =
    when {
        leaving == null -> SessionMode.Unknown
        leaving -> SessionMode.Leaving
        left -> SessionMode.LeftElsewhere
        else -> SessionMode.Open
    }
