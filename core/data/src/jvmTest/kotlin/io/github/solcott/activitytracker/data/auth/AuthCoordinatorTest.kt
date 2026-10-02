package io.github.solcott.activitytracker.data.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class AuthCoordinatorTest {
    @Test
    fun parsesRedirectQuery() {
        val params = parseRedirectParams("app.activitytracker:/oauth/callback?code=abc&state=s%2B1&iss=http%3A%2F%2Flocalhost%3A2583")
        assertEquals(
            mapOf("code" to "abc", "state" to "s+1", "iss" to "http://localhost:2583"),
            params,
        )
    }
}
