package io.github.solcott.activitytracker.atproto.space

import io.github.solcott.activitytracker.atproto.xrpc.XrpcBytes
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.atproto.SimpleSpace
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpaceAppAccess
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.github.solcott.activitytracker.lexicon.atproto.SpacePolicy
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonElement

/** The outcome of reading from a space: a refusal is an answer, not an error. Network and other XRPC failures still throw. */
sealed interface SpaceRead<out T> {
    data class Found<T>(val value: T) : SpaceRead<T>

    /** The space, repo, record or blob doesn't exist. */
    data object NotFound : SpaceRead<Nothing>

    /** The space's authority won't let this viewer read it. */
    data object NoAccess : SpaceRead<Nothing>
}

/** The outcome of [SpaceClient.createSpace]. Either way the space exists at [uri]. */
sealed interface CreateSpaceResult {
    val uri: SpaceUri

    data class Created(override val uri: SpaceUri) : CreateSpaceResult

    data class AlreadyExists(override val uri: SpaceUri) : CreateSpaceResult
}

/**
 * atproto Spaces for the signed-in athlete [did]: the only entry point to spaces.
 * - **Owner reads** of the athlete's own repo in their own spaces go to their PDS with the session ([pds], DPoP, the `read_self` scope).
 * - **Member reads** go to the repo owner's PDS with a credential from [credentials], signed with its binding key.
 * - **Writes** (records, and creating or updating spaces) go to the athlete's PDS with the session; an athlete writes only to their own
 *   repo in a space.
 *
 * A session signed in before the `space:` scope existed gets `ScopeMissingError` ([SpaceErrors.SCOPE_MISSING]) as an [XrpcException].
 */
class SpaceClient(
    val did: Did,
    private val pds: XrpcClient,
    credentials: SpaceCredentials,
    http: HttpClient,
) {
    private val member = SpaceMember(credentials, http)

    /** A member's read is returned only with a verified record MAC; otherwise it throws [SpaceMacException], never a [SpaceRead]. */
    suspend fun getRecord(space: SpaceUri, repo: Did, collection: String, rkey: String): SpaceRead<Space.GetRecord.Output> = outcome {
        if (space.authority == did && repo == did) pds.query(Space.GetRecord.method, Space.GetRecord.Params(space, repo, collection, rkey))
        else member.getRecord(space, repo, collection, rkey)
    }

    suspend fun listRecords(
        space: SpaceUri,
        repo: Did,
        collection: String? = null,
        limit: Int? = null,
        cursor: String? = null,
    ): SpaceRead<Space.ListRecords.Output> =
        read(space, repo) { it.query(Space.ListRecords.method, Space.ListRecords.Params(space, repo, collection, limit, cursor)) }

    /**
     * Every [collection] record of [repo] in [space], following cursors [pageSize] at a time. A refusal or a missing space part-way answers
     * as it would for the first page, so callers never see a partial list as complete.
     */
    suspend fun listAllRecords(space: SpaceUri, repo: Did, collection: String, pageSize: Int): SpaceRead<List<Space.ListRecords.Record>> {
        val records = mutableListOf<Space.ListRecords.Record>()
        var failed: SpaceRead<Nothing>? = null
        var cursor: String? = null
        do {
            when (val read = listRecords(space, repo, collection, pageSize, cursor)) {
                is SpaceRead.Found -> {
                    records += read.value.records
                    cursor = read.value.cursor?.takeIf { read.value.records.isNotEmpty() }
                }
                SpaceRead.NotFound -> {
                    failed = SpaceRead.NotFound
                    cursor = null
                }
                SpaceRead.NoAccess -> {
                    failed = SpaceRead.NoAccess
                    cursor = null
                }
            }
        } while (cursor != null)
        return failed ?: SpaceRead.Found(records)
    }

    suspend fun getBlob(space: SpaceUri, repo: Did, cid: Cid): SpaceRead<XrpcBytes> =
        read(space, repo) { it.queryBytes(Space.GetBlob.method, Space.GetBlob.Params(space, repo, cid)) }

    /** Creates one of the athlete's spaces, or reports that it already exists, so callers can create spaces lazily. */
    suspend fun createSpace(
        type: String,
        skey: String,
        readPolicy: SpacePolicy,
        writePolicy: SpacePolicy,
        appAccess: SpaceAppAccess = SpaceAppAccess.Open(),
    ): CreateSpaceResult =
        try {
            val input = SimpleSpace.CreateSpace.Input(type, skey, readPolicy, writePolicy, appAccess)
            CreateSpaceResult.Created(pds.procedure(SimpleSpace.CreateSpace.method, input).uri)
        } catch (e: XrpcException) {
            if (e.error != SpaceErrors.SPACE_ALREADY_EXISTS) throw e
            CreateSpaceResult.AlreadyExists(SpaceUri.of(did, type, skey))
        }

    /** The athlete's space at [space] and its policies, or null if there is none. */
    suspend fun getSpace(space: SpaceUri): SimpleSpace.GetSpace.Output? =
        try {
            pds.query(SimpleSpace.GetSpace.method, SimpleSpace.GetSpace.Params(space))
        } catch (e: XrpcException) {
            if (e.error != SpaceErrors.SPACE_NOT_FOUND) throw e
            null
        }

    /** Changes the athlete's space at [space]: each policy given replaces the current one, an omitted one stays. */
    suspend fun updateSpace(
        space: SpaceUri,
        readPolicy: SpacePolicy? = null,
        writePolicy: SpacePolicy? = null,
        appAccess: SpaceAppAccess? = null,
    ) {
        pds.procedure(SimpleSpace.UpdateSpace.method, SimpleSpace.UpdateSpace.Input(space, readPolicy, writePolicy, appAccess))
    }

    /** Writes [record] to the athlete's repo in [space]. Idempotent: the same put twice leaves one record. */
    suspend fun putRecord(space: SpaceUri, collection: String, rkey: String, record: JsonElement): Space.PutRecord.Output =
        pds.procedure(Space.PutRecord.method, Space.PutRecord.Input(space, did, collection, rkey, record))

    suspend fun deleteRecord(space: SpaceUri, collection: String, rkey: String) {
        pds.procedure(Space.DeleteRecord.method, Space.DeleteRecord.Input(space, did, collection, rkey))
    }

    private suspend fun <T> read(space: SpaceUri, repo: Did, call: suspend (XrpcClient) -> T): SpaceRead<T> = outcome {
        // The athlete's own repo in their own space: read with the session, with no credential or MAC.
        if (space.authority == did && repo == did) call(pds) else member.call(space, repo, call)
    }

    private suspend fun <T> outcome(read: suspend () -> T): SpaceRead<T> =
        try {
            SpaceRead.Found(read())
        } catch (e: XrpcException) {
            when (e.error) {
                in SpaceErrors.denials -> SpaceRead.NoAccess
                in NOT_FOUND -> SpaceRead.NotFound
                else -> throw e
            }
        }

    private companion object {
        val NOT_FOUND = setOf(SpaceErrors.RECORD_NOT_FOUND, SpaceErrors.BLOB_NOT_FOUND, SpaceErrors.SPACE_NOT_FOUND, "RepoNotFound")
    }
}
