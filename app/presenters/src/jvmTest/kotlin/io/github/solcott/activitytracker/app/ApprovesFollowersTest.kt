package io.github.solcott.activitytracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.profile.rememberApprovesFollowers
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.profile.TrainingNarrowing
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

class ApprovesFollowersTest {
    private val alice = Did("did:plc:alice")
    private var failPds = false
    private var recordReads = 0
    private val answered = CompletableDeferred<Unit>()
    private var enabled by mutableStateOf(false)

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.repo.getRecord" -> {
                        recordReads++
                        answered.complete(Unit)
                        if (failPds) respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        else
                            respond(
                                """{"uri":"at://$alice/${Nsid.ACTOR_PROFILE}/self","value":{"followPolicy":"approve"}}""",
                                headers = json,
                            )
                    }

                    else -> {
                        respond("", HttpStatusCode.NotFound)
                    }
                }
            }
        ) {
            expectSuccess = false
        }

    private data class Shown(val approves: Boolean) : CircuitUiState

    private suspend fun profiles() =
        ProfileRepository(signedInTestSession(http, did = alice).clients, Clock.System, Logger, TrainingNarrowing {})

    @Test
    fun readsThePolicyOnceEnabled() = runTest {
        val repo = profiles()
        presenterTestOf({ Shown(rememberApprovesFollowers(repo, enabled)) }) {
            assertFalse(awaitItem().approves)
            assertEquals(0, recordReads, "nothing read while disabled")

            enabled = true
            assertTrue(awaitUntil { it.approves }.approves)
            assertEquals(1, recordReads)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedReadIsFalse() = runTest {
        failPds = true
        enabled = true
        val repo = profiles()
        presenterTestOf({ Shown(rememberApprovesFollowers(repo, enabled)) }) {
            assertFalse(awaitItem().approves)
            answered.await()
            assertEquals(1, recordReads)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
