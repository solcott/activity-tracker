package io.github.solcott.activitytracker.app

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.app.auth.AuthBrowser
import kotlinx.browser.window

/** Full-page redirect; the pending authorization was persisted before leaving. */
@ContributesBinding(AppScope::class)
@Inject
class WebAuthBrowser : AuthBrowser {
    override fun open(url: String) {
        window.location.href = url
    }
}
