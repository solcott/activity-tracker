package io.github.solcott.activitytracker.app.ui

/** Tells the feed's map images who is signed in, so signing out drops them (feed/feed-route-map); `null` when signed out. */
expect suspend fun onRouteImageSession(did: String?)
