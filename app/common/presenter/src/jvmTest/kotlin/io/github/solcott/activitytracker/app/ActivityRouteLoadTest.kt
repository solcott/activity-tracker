package io.github.solcott.activitytracker.app

import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.LoadStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityRouteLoadTest {
    @Test
    fun aLoadFailureNamesTheReadThatFailed() {
        fun load(activityFailed: Boolean, trackFailed: Boolean) =
            ActivityRouteLoad(ContentState<ActivityRoute?>(null, null, LoadStatus.Loading), activityFailed, trackFailed, retry = {})

        assertEquals(UiError.Kind.LoadActivity, load(activityFailed = true, trackFailed = true).loadErrorKind(UiError.Kind.LoadRoute))
        assertEquals(UiError.Kind.LoadRoute, load(activityFailed = false, trackFailed = true).loadErrorKind(UiError.Kind.LoadRoute))
        assertEquals(UiError.Kind.LoadPrivacyZones, load(activityFailed = false, trackFailed = false).loadErrorKind(UiError.Kind.LoadRoute))
    }
}
