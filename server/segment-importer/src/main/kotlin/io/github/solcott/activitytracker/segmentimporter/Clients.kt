package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.atproto.xrpc.ServiceAuthAuthenticator
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidates
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode

/**
 * The service account's two connections: its PDS, signed in with [PasswordSession], and the AppView, with service-auth tokens that PDS
 * mints for [Settings.appViewDid] and each method.
 */
internal class Clients(http: HttpClient, settings: Settings) {
    val session = PasswordSession(http, settings.pdsUrl, settings.identifier, settings.password)
    val pds = XrpcClient(http, settings.pdsUrl, session)
    val appView = XrpcClient(http, settings.appViewUrl, ServiceAuthAuthenticator({ pds }, settings.appViewDid))

    /** [block], with the AppView's `NotImporter` refusal turned into [NotImporter]. */
    suspend fun <T> asImporter(block: suspend () -> T): T =
        try {
            block()
        } catch (e: XrpcException) {
            if (e.status == HttpStatusCode.Forbidden.value && e.error == SegmentCandidates.NOT_IMPORTER) throw NotImporter(e)
            throw e
        }

    class NotImporter(cause: Throwable) :
        Exception(
            "The AppView doesn't accept this account as the segment importer: set its SEGMENT_IMPORTER_DID to the account's DID",
            cause,
        )
}
