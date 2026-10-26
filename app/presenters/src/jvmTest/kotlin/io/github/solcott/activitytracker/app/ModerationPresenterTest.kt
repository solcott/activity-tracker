package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.moderation.ModerationEvent
import io.github.solcott.activitytracker.app.moderation.ModerationPresenter
import io.github.solcott.activitytracker.app.moderation.ModerationScreen
import io.github.solcott.activitytracker.app.moderation.ModerationSubject
import io.github.solcott.activitytracker.app.settings.SettingsEvent
import io.github.solcott.activitytracker.app.settings.SettingsPresenter
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.moderation.ModerationRepository
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/** The Moderation screen (`moderation/private-subjects`): opening a subject by URI, and "not under review" for the AppView's NotFound. */
class ModerationPresenterTest {
    private val followersActivity = "at://did:plc:bob/${Nsid.FEED_ACTIVITY}/3kf"
    private var role: String? = "moderator"
    private var appViewDown = false
    private var subjectRequests = 0

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/${Nsid.MODERATION_GET_ROLE}" -> respond(role?.let { """{"role":"$it"}""" } ?: "{}", headers = json)
                    "/xrpc/${Nsid.MODERATION_GET_SUBJECT}" -> {
                        subjectRequests++
                        when {
                            appViewDown -> respond("""{"error":"LabelerUnavailable"}""", HttpStatusCode.ServiceUnavailable, json)
                            request.url.parameters["uri"] == followersActivity ->
                                respond(
                                    """{"visibility":"followers","activity":{"uri":"$followersActivity","cid":"bafy3kf",""" +
                                        """"author":{"did":"did:plc:bob","handle":"bob.test"},"record":{"sport":"run",""" +
                                        """"startedAt":"2026-10-02T07:00:00Z","elapsedTime":1800,"createdAt":"2026-10-02T07:31:00Z"},""" +
                                        """"likeCount":0,"commentCount":0,"indexedAt":"2026-10-02T07:32:00Z","visibility":"followers"}}""",
                                    headers = json,
                                )
                            else ->
                                respond(
                                    """{"error":"NotFound","message":"No such subject under review"}""",
                                    HttpStatusCode.BadRequest,
                                    json,
                                )
                        }
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        ) {
            expectSuccess = false
        }

    private suspend fun moderation() = ModerationRepository(signedInTestSession(http, labelerDid = Did("did:plc:labeler")).clients)

    @Test
    fun opensAReportedFollowersActivity() = runTest {
        val repository = moderation()
        presenterTestOf({ ModerationPresenter(FakeNavigator(ModerationScreen), repository) }) {
            val initial = awaitItem()
            assertNull(initial.subject)
            initial.uri.setTextAndPlaceCursorAtEnd("  $followersActivity ")
            initial.eventSink(ModerationEvent.Open)
            val opened = awaitUntil { it.subject != null }
            val subject = assertIs<ModerationSubject.Activity>(opened.subject)
            assertEquals("followers", subject.visibility)
            assertEquals(followersActivity, subject.activity.uri.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun notFoundShowsAsNotUnderReview() = runTest {
        val repository = moderation()
        presenterTestOf({ ModerationPresenter(FakeNavigator(ModerationScreen), repository) }) {
            val initial = awaitItem()
            initial.uri.setTextAndPlaceCursorAtEnd("at://did:plc:bob/${Nsid.FEED_ACTIVITY}/3kunreported")
            initial.eventSink(ModerationEvent.Open)
            assertEquals(ModerationSubject.NotUnderReview, awaitUntil { it.subject != null }.subject)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRefusalOrABadUriIsAnError() = runTest {
        appViewDown = true
        val repository = moderation()
        presenterTestOf({ ModerationPresenter(FakeNavigator(ModerationScreen), repository) }) {
            val initial = awaitItem()
            initial.uri.setTextAndPlaceCursorAtEnd(followersActivity)
            initial.eventSink(ModerationEvent.Open)
            assertEquals(UiError.Kind.OpenSubject, awaitUntil { it.error != null }.error?.kind)
            val requested = subjectRequests
            initial.uri.setTextAndPlaceCursorAtEnd("not a uri")
            initial.eventSink(ModerationEvent.Open)
            assertNull(awaitUntil { it.error?.kind == UiError.Kind.InvalidSubjectUri }.subject)
            assertEquals(requested, subjectRequests)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun settingsOffersModerationOnlyToModerators() = runTest {
        val fixture = SessionTestFixture()
        val repos = fixture.repos()
        val navigator = FakeNavigator(SettingsScreen)
        val forModerator = moderation()
        presenterTestOf({
            SettingsPresenter(
                navigator,
                repos.units,
                repos.auth,
                repos.defaults,
                repos.privacy,
                forModerator,
                repos.segmentPrefs,
                repos.leaderboardBrackets,
            )
        }) {
            val offered = awaitUntil { it.canModerate }
            offered.eventSink(SettingsEvent.OpenModeration)
            assertEquals(ModerationScreen, navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
        role = "triage"
        val forTriage = moderation()
        presenterTestOf({
            SettingsPresenter(
                navigator,
                repos.units,
                repos.auth,
                repos.defaults,
                repos.privacy,
                forTriage,
                repos.segmentPrefs,
                repos.leaderboardBrackets,
            )
        }) {
            val state = awaitItem()
            state.eventSink(SettingsEvent.OpenModeration)
            assertEquals(false, state.canModerate)
            navigator.expectNoGoToEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
