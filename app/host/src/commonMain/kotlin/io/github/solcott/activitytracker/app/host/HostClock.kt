package io.github.solcott.activitytracker.app.host

/** How the host's compositions are paced. */
enum class HostClock {
    /** Once a frame at most, from the frame clock in the caller's coroutine context: what an app uses. */
    Frame,

    /** As soon as state changes, on the caller's dispatcher: what tests use, on a test dispatcher. */
    Immediate,
}
