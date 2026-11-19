package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.atproto.oauth.isSecureEndpoint
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OAuthEndpointTest {
    @Test
    fun httpsEndpointsAreUsed() {
        assertTrue(isSecureEndpoint("https://bsky.social/oauth/authorize"))
        assertTrue(isSecureEndpoint("HTTPS://pds.example/oauth/par"))
    }

    @Test
    fun plainHttpIsUsedOnlyOnLoopbackHostsLikeTheLocalStacksPds() {
        assertTrue(isSecureEndpoint("http://localhost:2583/oauth/authorize"))
        assertTrue(isSecureEndpoint("http://127.0.0.1:2583/oauth/token"))
        assertTrue(isSecureEndpoint("http://[::1]:2583/oauth/token"))
        assertFalse(isSecureEndpoint("http://pds.example/oauth/authorize"))
        assertFalse(isSecureEndpoint("http://localhost.evil.example/oauth/authorize"))
    }

    @Test
    fun otherSchemesAreRefused() {
        assertFalse(isSecureEndpoint("javascript:alert(document.domain)"))
        assertFalse(isSecureEndpoint("JavaScript://pds.example/%0aalert(1)"))
        assertFalse(isSecureEndpoint("data:text/html,<script>alert(1)</script>"))
        assertFalse(isSecureEndpoint("activitytracker://oauth/callback"))
        assertFalse(isSecureEndpoint(""))
    }
}
