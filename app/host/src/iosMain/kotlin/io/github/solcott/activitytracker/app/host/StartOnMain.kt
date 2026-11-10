package io.github.solcott.activitytracker.app.host

import app.cash.molecule.DisplayLinkClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Runs the app's screens for as long as the app lives: on the main thread, where presenters run, and recomposing once a frame at most,
 * paced by the display. The iOS app calls it once, before it shows anything.
 */
fun AppHost.startOnMain(sessions: SessionHostFactory): Job = MainScope().launch(DisplayLinkClock) { run(HostClock.Frame, sessions) }
