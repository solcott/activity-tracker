package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.moderation.ReportReason
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.CreateReport
import io.github.solcott.activitytracker.lexicon.atproto.ReportSubject
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Reports (`moderation/reports`): what goes to the labeler, through the athlete's PDS. */
class ReportRepositoryTest {
    private val alice = Did("did:plc:alice")
    private val bob = Did("did:plc:bob")
    private val labeler = Did("did:plc:labeler")
    private val activity = ReportTarget.Activity(AtUri.of(bob, Nsid.FEED_ACTIVITY, "3k"), Cid("bafyact"), bob)

    private val requests = mutableListOf<HttpRequestData>()
    private var failReports = false

    private val http =
        HttpClient(
            MockEngine { request ->
                requests += request
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.moderation.createReport" ->
                        if (failReports) {
                            respond("""{"error":"UpstreamFailure"}""", HttpStatusCode.BadGateway, json)
                        } else {
                            respond(
                                """{"id":1,"reasonType":"x","reportedBy":"$alice","createdAt":"2026-10-20T10:00:00Z",
                                  "subject":{"${'$'}type":"com.atproto.admin.defs#repoRef","did":"$bob"}}""",
                                headers = json,
                            )
                        }
                    else -> respond("""{"error":"NotFound"}""", HttpStatusCode.NotFound, json)
                }
            }
        )

    private suspend fun reports(labelerDid: Did? = labeler) =
        ReportRepository(signedInTestSession(http, alice, labelerDid = labelerDid).clients)

    private val sent: CreateReport.Input
        get() {
            val request = requests.last { it.url.encodedPath.endsWith("createReport") }
            return AtJson.decodeFromString(CreateReport.Input.serializer(), (request.body as TextContent).text)
        }

    @Test
    fun anActivityGoesToTheAthletesPdsProxiedToTheLabeler() = runTest {
        reports().report(activity, ReportReason.SPAM)
        val request = requests.single { it.url.encodedPath.endsWith("createReport") }
        assertEquals("pds.test", request.url.host)
        assertEquals("did:plc:labeler#atproto_labeler", request.headers["atproto-proxy"])
        assertEquals(CreateReport.Input("com.atproto.moderation.defs#reasonSpam", ReportSubject.Record(activity.uri, activity.cid)), sent)
    }

    @Test
    fun aCommentIsNamedByStrongRef() = runTest {
        val comment = ReportTarget.Comment(AtUri.of(bob, Nsid.FEED_COMMENT, "3c"), Cid("bafycom"), bob)
        reports().report(comment, ReportReason.HARASSMENT)
        assertEquals(ReportSubject.Record(comment.uri, comment.cid), sent.subject)
        assertEquals("com.atproto.moderation.defs#reasonRude", sent.reasonType)
        assertEquals(null, sent.reason)
    }

    @Test
    fun aPhotoNamesItsActivityAndThePositionInTheNote() = runTest {
        reports().report(ReportTarget.Photo(activity, 3), ReportReason.SEXUAL, "  not ok ")
        assertEquals(ReportSubject.Record(activity.uri, activity.cid), sent.subject)
        assertEquals("Photo 3\n\nnot ok", sent.reason)

        reports().report(ReportTarget.Photo(activity, 1), ReportReason.VIOLENCE)
        assertEquals("Photo 1", sent.reason)
        assertEquals("com.atproto.moderation.defs#reasonViolation", sent.reasonType)
    }

    @Test
    fun anAccountIsNamedByRepoRef() = runTest {
        reports().report(ReportTarget.Account(bob), ReportReason.MISLEADING)
        assertEquals(ReportSubject.Repo(bob), sent.subject)
        assertEquals("com.atproto.moderation.defs#reasonMisleading", sent.reasonType)
    }

    @Test
    fun otherNeedsANote() = runTest {
        val repository = reports()
        assertFailsWith<IllegalArgumentException> { repository.report(activity, ReportReason.OTHER, "   ") }
        assertTrue(requests.none { it.url.encodedPath.endsWith("createReport") })
        repository.report(activity, ReportReason.OTHER, "Stolen route")
        assertEquals("com.atproto.moderation.defs#reasonOther", sent.reasonType)
        assertEquals("Stolen route", sent.reason)
    }

    @Test
    fun theNoteIsCapped() = runTest {
        reports().report(activity, ReportReason.SPAM, "x".repeat(5000))
        assertEquals(ReportRepository.MAX_NOTE_LENGTH, sent.reason?.length)
    }

    @Test
    fun ownContentAndAMissingLabelerAreRefused() = runTest {
        assertFailsWith<IllegalArgumentException> { reports().report(ReportTarget.Account(alice), ReportReason.SPAM) }
        val unconfigured = reports(labelerDid = null)
        assertFalse(unconfigured.canReport)
        assertFailsWith<IllegalStateException> { unconfigured.report(activity, ReportReason.SPAM) }
        assertTrue(requests.none { it.url.encodedPath.endsWith("createReport") })
    }

    @Test
    fun aFailureSurfaces() = runTest {
        failReports = true
        assertFailsWith<XrpcException> { reports().report(activity, ReportReason.SPAM) }
    }

    private companion object {
        val json = headersOf("Content-Type", "application/json")
    }
}
