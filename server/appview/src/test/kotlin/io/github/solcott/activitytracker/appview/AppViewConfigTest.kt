package io.github.solcott.activitytracker.appview

import kotlin.test.Test
import kotlin.test.assertEquals

class AppViewConfigTest {
    @Test
    fun managingAppServiceDefaultsToLocalhostDid() {
        assertEquals("did:web:localhost%3A8081#activitytracker_space", AppViewConfig.fromEnv(emptyMap()).managingAppService)
    }

    @Test
    fun managingAppServiceFollowsPublicUrl() {
        val config = AppViewConfig.fromEnv(mapOf("APPVIEW_PUBLIC_URL" to "https://quick-tunnel.trycloudflare.com"))
        assertEquals("did:web:quick-tunnel.trycloudflare.com#activitytracker_space", config.managingAppService)
    }

    @Test
    fun managingAppServiceFollowsExplicitDid() {
        val config = AppViewConfig.fromEnv(mapOf("APPVIEW_DID" to "did:web:appview.example.com"))
        assertEquals("did:web:appview.example.com#activitytracker_space", config.managingAppService)
    }
}
