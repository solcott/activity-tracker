package io.github.solcott.activitytracker.appview.moderation

import io.github.solcott.activitytracker.appview.AppViewConfig
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.lexicon.AtJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The few Ozone calls the AppView makes (`moderation/private-subjects`), as its own [triage] account on the labeler's team: it signs in to
 * that account's PDS with an app password, and each call carries service auth the PDS mints for the labeler and that method. Any failure
 * throws [LabelerUnavailable], so callers refuse rather than serve.
 */
class OzoneClient(
    private val http: HttpClient,
    private val labeler: AppViewConfig.Labeler,
    private val triage: AppViewConfig.Triage,
    /** A DID's `#atproto_pds` endpoint, and the labeler's `#atproto_labeler` when [labeler] names no URL. */
    private val endpoint: suspend (did: String, service: String) -> String?,
) {
    private val mutex = Mutex()
    private var session: String? = null

    /** The labeler's team, every page. */
    @Suppress("RedundantSuspendModifier") // It suspends in call(), which detekt's type resolution misses here.
    suspend fun members(): List<Member> {
        val members = mutableListOf<Member>()
        var cursor: String? = null
        do {
            val page = call(LIST_MEMBERS, ListMembers.serializer(), mapOf("limit" to "100", "cursor" to cursor))
            members += page.members
            cursor = page.cursor?.takeIf { page.members.isNotEmpty() }
        } while (cursor != null)
        return members
    }

    /** The review status Ozone holds for [subject], an AT-URI or a DID; empty when it has none. */
    @Suppress("RedundantSuspendModifier") // As members().
    suspend fun statuses(subject: String): List<SubjectStatus> =
        call(QUERY_STATUSES, QueryStatuses.serializer(), mapOf("subject" to subject, "limit" to "10")).subjectStatuses

    private suspend fun <T> call(method: String, output: KSerializer<T>, params: Map<String, String?>): T {
        val response = runSuspendCatching {
            val base = requireNotNull(labeler.url ?: endpoint(labeler.did, LABELER_SERVICE)) { "No labeler endpoint" }
            http.get("$base/xrpc/$method") {
                header(HttpHeaders.Authorization, "Bearer ${serviceAuth(method)}")
                params.forEach { (k, v) -> v?.let { parameter(k, it) } }
            }
        }
            // Network failures of every kind, and no endpoint: the caller refuses.
            .getOrElse { e -> throw e as? LabelerUnavailable ?: LabelerUnavailable("$method: ${e.message}", e) }
        return decode(response, output, method)
    }

    /** Service auth for [method] at the labeler, minted by the triage account's PDS; one sign-in again if the session lapsed. */
    private suspend fun serviceAuth(method: String): String {
        repeat(2) { attempt ->
            val pds = pds()
            val response =
                http.get("$pds/xrpc/com.atproto.server.getServiceAuth") {
                    header(HttpHeaders.Authorization, "Bearer ${session(fresh = attempt > 0)}")
                    parameter("aud", labeler.did)
                    parameter("lxm", method)
                }
            if (response.status.isSuccess()) return decode(response, ServiceAuth.serializer(), "getServiceAuth").token
            if (response.status != HttpStatusCode.Unauthorized && response.status != HttpStatusCode.BadRequest) {
                throw LabelerUnavailable("getServiceAuth: ${response.status}")
            }
        }
        throw LabelerUnavailable("The triage account can't sign in")
    }

    private suspend fun session(fresh: Boolean): String = mutex.withLock {
        session?.takeUnless { fresh }
            ?: run {
                val response =
                    http.post("${pds()}/xrpc/com.atproto.server.createSession") {
                        contentType(ContentType.Application.Json)
                        setBody(AtJson.encodeToString(CreateSession.serializer(), CreateSession(triage.did, triage.appPassword)))
                    }
                decode(response, Session.serializer(), "createSession").accessJwt.also { session = it }
            }
    }

    private suspend fun pds(): String = endpoint(triage.did, PDS_SERVICE) ?: throw LabelerUnavailable("No PDS for ${triage.did}")

    private suspend fun <T> decode(response: HttpResponse, output: KSerializer<T>, method: String): T {
        if (!response.status.isSuccess()) throw LabelerUnavailable("$method: ${response.status}")
        return runCatching { AtJson.decodeFromString(output, response.bodyAsText()) }
            .getOrElse { throw LabelerUnavailable("$method: $it", it) }
    }

    /** `tools.ozone.team.defs#member`, the fields the AppView reads. */
    @Serializable data class Member(val did: String, val role: String, val disabled: Boolean? = null)

    /** `tools.ozone.moderation.defs#subjectStatusView`, the fields the AppView reads. */
    @Serializable data class SubjectStatus(val id: Long, val reviewState: String, val subject: JsonObject? = null)

    @Serializable private data class ListMembers(val members: List<Member> = emptyList(), val cursor: String? = null)

    @Serializable private data class QueryStatuses(val subjectStatuses: List<SubjectStatus> = emptyList())

    @Serializable private data class CreateSession(val identifier: String, val password: String)

    @Serializable private data class Session(val accessJwt: String)

    @Serializable private data class ServiceAuth(val token: String)

    class LabelerUnavailable(message: String, cause: Throwable? = null) : Exception(message, cause)

    companion object {
        const val LIST_MEMBERS = "tools.ozone.team.listMembers"
        const val QUERY_STATUSES = "tools.ozone.moderation.queryStatuses"
        const val PDS_SERVICE = "#atproto_pds"
        const val LABELER_SERVICE = "#atproto_labeler"

        /** Review states that mean a report or review is still open. */
        val OPEN_REVIEW = setOf("tools.ozone.moderation.defs#reviewOpen", "tools.ozone.moderation.defs#reviewEscalated")
    }
}
