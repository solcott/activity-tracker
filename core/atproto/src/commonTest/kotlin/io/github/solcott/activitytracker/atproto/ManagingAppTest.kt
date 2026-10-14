package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.atproto.space.ManagingApp
import io.github.solcott.activitytracker.atproto.space.ManagingAppException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class ManagingAppTest {
    private val network = MockNetwork()

    private fun serves(services: String) {
        network.on("localhost/.well-known/did.json") { respondJson("""{"id":"did:web:tunnel.example","service":[$services]}""") }
    }

    @Test
    fun idComesFromTheDocumentNotTheUrl() = runTest {
        serves(
            """{"id":"#activity_appview","type":"ActivityTrackerAppView","serviceEndpoint":"https://tunnel.example"},
               {"id":"#activitytracker_space","type":"AtprotoSpaceService","serviceEndpoint":"https://tunnel.example"}"""
        )
        assertEquals("did:web:tunnel.example#activitytracker_space", ManagingApp.resolve(network.client, "http://localhost:8081/"))
    }

    @Test
    fun missingServiceFails() = runTest {
        serves("""{"id":"#activity_appview","type":"ActivityTrackerAppView","serviceEndpoint":"https://tunnel.example"}""")
        assertFailsWith<ManagingAppException> { ManagingApp.resolve(network.client, "http://localhost:8081") }
    }

    @Test
    fun wrongServiceTypeFails() = runTest {
        serves("""{"id":"#activitytracker_space","type":"SomethingElse","serviceEndpoint":"https://tunnel.example"}""")
        assertFailsWith<ManagingAppException> { ManagingApp.resolve(network.client, "http://localhost:8081") }
    }
}
