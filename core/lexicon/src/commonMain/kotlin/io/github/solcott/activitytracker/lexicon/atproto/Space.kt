package io.github.solcott.activitytracker.lexicon.atproto

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.NoOutput
import io.github.solcott.activitytracker.lexicon.XrpcProcedure
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import io.github.solcott.activitytracker.lexicon.app.OpenUnionSerializer
import kotlin.io.encoding.Base64
import kotlin.jvm.JvmInline
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject

/*
 * atproto Spaces (permissioned repos), mirrored from the `com.atproto.space.*` lexicons and the `personalspace`, `memberspace` and
 * `managedspace` policies in the PDS image pinned in docker/pds.Dockerfile. The protocol is an alpha that changes weekly, so every
 * Spaces wire type lives in this file; bump it together with the image.
 */

/** `space-ref`: `at://{authority}/space/{type}/{skey}`, one space of a type under its authority. */
@Serializable
@JvmInline
value class SpaceUri(val value: String) {
    init {
        val parts = value.removePrefix(PREFIX).split('/')
        require(value.startsWith(PREFIX) && parts.size == PARTS && parts[1] == SEGMENT && parts.none(String::isEmpty)) {
            "Not a space URI: $value"
        }
        require(parts[0].startsWith("did:")) { "A space's authority must be a DID: $value" }
    }

    private val parts: List<String>
        get() = value.removePrefix(PREFIX).split('/')

    val authority: Did
        get() = Did(parts[0])

    /** The space type's NSID. */
    val type: String
        get() = parts[2]

    /** The space key, which tells apart spaces of one type under one authority. */
    val skey: String
        get() = parts[3]

    override fun toString(): String = value

    companion object {
        private const val PREFIX = "at://"
        private const val SEGMENT = "space"

        /** Authority, `space`, type and key. */
        private const val PARTS = 4

        fun of(authority: Did, type: String, skey: String) = SpaceUri("$PREFIX$authority/$SEGMENT/$type/$skey")
    }
}

/**
 * Error names the Spaces methods and their auth layer return, as they appear in [io.github.solcott.activitytracker.lexicon.XrpcErrorBody].
 */
object SpaceErrors {
    /**
     * `authorizeSpaceSync`: the authority refused this account. The reference authority answers [SPACE_NOT_FOUND] instead, so a refusal
     * discloses nothing; other authorities may still send this.
     */
    const val ACCOUNT_NOT_AUTHORIZED = "AccountNotAuthorized"

    /** The authority refused this app. */
    const val APP_NOT_AUTHORIZED = "AppNotAuthorized"

    /**
     * The authority refused without saying whether the account or the app failed; also what the authority-only methods (`updateSpace`,
     * `deleteSpace`, `getSpace`, …) answer anyone else.
     */
    const val NOT_AUTHORIZED = "NotAuthorized"

    /** The credential has expired; get a new one. */
    const val JWT_EXPIRED = "JwtExpired"

    /** The authority revoked the credential; get a new one. */
    const val CREDENTIAL_REVOKED = "CredentialRevoked"

    /** The `Authorization` scheme or credential wasn't accepted (the pre-Oct 22 `Atproto-Space` scheme among them). */
    const val INVALID_TOKEN = "InvalidToken"

    /** `authorizeSpaceSync`: the delegation token was malformed, expired or already used (HTTP 400). */
    const val INVALID_DELEGATION_TOKEN = "InvalidDelegationToken"

    /** A credentialed read's HTTP message signature didn't verify against the credential's key. */
    const val BAD_SPACE_SIGNATURE = "BadSpaceSignature"

    /** A credentialed read's `Atproto-Space-Audience` wasn't the repo owner (or, at a space host, the authority). */
    const val BAD_SPACE_AUDIENCE = "BadSpaceAudience"

    /** A credentialed read of a MAC'd method came without a usable `Accept-Signature` MAC request (HTTP 400). */
    const val INVALID_SPACE_MAC = "InvalidSpaceMac"

    /**
     * `authorizeSpaceSync` with `notify`: the space host couldn't confirm the registration with `confirmRegistration` at the endpoint
     * named. No credential is issued.
     */
    const val REGISTRATION_NOT_CONFIRMED = "RegistrationNotConfirmed"

    /** The space host couldn't get an answer from the space's managing app (unreachable, an error, or 10 s without one). */
    const val UPSTREAM_FAILURE = "UpstreamFailure"

    /** The OAuth session wasn't granted the `space:` scope this call needs. */
    const val SCOPE_MISSING = "ScopeMissingError"

    const val SPACE_ALREADY_EXISTS = "SpaceAlreadyExists"
    /** No such space, or (from `authorizeSpaceSync`) the authority refused the account: the two look the same on purpose. */
    const val SPACE_NOT_FOUND = "SpaceNotFound"

    /** From `authorizeSpaceSync`: the space was deleted, and a syncer should drop it. */
    const val SPACE_DELETED = "SpaceDeleted"

    /**
     * From `authorizeSpaceSync` (HTTP 400): the authority is taken down. The space is suspended, not deleted, and resumes if the authority
     * is reinstated, so a syncer keeps it and retries.
     */
    const val REPO_TAKENDOWN = "RepoTakendown"

    /** From `authorizeSpaceSync` (HTTP 400): the authority is deactivated. Suspended like [REPO_TAKENDOWN]. */
    const val REPO_DEACTIVATED = "RepoDeactivated"
    const val RECORD_NOT_FOUND = "RecordNotFound"
    const val BLOB_NOT_FOUND = "BlobNotFound"

    /** `createSpace`/`updateSpace`: the host doesn't implement the requested policy. */
    const val UNSUPPORTED_POLICY = "UnsupportedPolicy"

    /** `memberspace.*` methods on a space governed by another policy. */
    const val POLICY_MISMATCH = "PolicyMismatch"

    /** Errors meaning the viewer may not read the space. */
    val denials = setOf(ACCOUNT_NOT_AUTHORIZED, APP_NOT_AUTHORIZED, NOT_AUTHORIZED)
}

/**
 * The policy governing a space, the `policy` union of `com.atproto.space.createSpace`/`getSpace`/`updateSpace`: its `$type` picks the
 * policy and its fields configure it. An open union, so a policy a newer PDS reports decodes as [Unknown]. Fields default as in the
 * lexicons, so a policy the PDS returns with its defaults filled in equals the one built here.
 */
@Serializable(with = SpacePolicySerializer::class)
sealed interface SpacePolicy {
    /** `com.atproto.personalspace.policy`: only the authority reads, and no other writers are tracked. */
    @Serializable
    data class Personal(@SerialName("\$type") val type: String = TYPE) : SpacePolicy {
        companion object {
            const val TYPE = "com.atproto.personalspace.policy"
        }
    }

    /**
     * `com.atproto.memberspace.policy`: members the authority adds with `memberspace.putMembers`, or anyone with
     * [publicRead]/[publicWrite].
     */
    @Serializable
    data class Member(
        val publicRead: Boolean = false,
        val publicWrite: Boolean = false,
        /** Attested OAuth client ids (URIs) that may read; null admits any app without an attestation. Doesn't apply to writes. */
        val allowedApps: List<String>? = null,
        /** Publishes the policy as a `com.atproto.space.policy` record in the authority's repo in the space. */
        val publishPolicy: Boolean = false,
        /** Publishes the member list as `com.atproto.memberspace.member` records in the authority's repo in the space. */
        val publishMembers: Boolean = false,
        @SerialName("\$type") val type: String = TYPE,
    ) : SpacePolicy {
        companion object {
            const val TYPE = "com.atproto.memberspace.policy"
        }
    }

    /** `com.atproto.managedspace.policy`: the PDS asks [managingApp] about every read and write through `managedspace.checkAccess`. */
    @Serializable
    data class Managed(
        /**
         * The managing app's service, a `did#serviceId` reference (`did:web:host#activitytracker_space`). `createSpace` and `updateSpace`
         * reject a bare DID.
         */
        val managingApp: String,
        /** Publishes the policy as a `com.atproto.space.policy` record in the authority's repo in the space. */
        val publishPolicy: Boolean = false,
        @SerialName("\$type") val type: String = TYPE,
    ) : SpacePolicy {
        companion object {
            const val TYPE = "com.atproto.managedspace.policy"
        }
    }

    data class Unknown(val json: JsonObject) : SpacePolicy
}

internal object SpacePolicySerializer : OpenUnionSerializer<SpacePolicy>("SpacePolicy") {
    override fun memberFor(type: String?) =
        when (type) {
            SpacePolicy.Personal.TYPE -> SpacePolicy.Personal.serializer()
            SpacePolicy.Member.TYPE -> SpacePolicy.Member.serializer()
            SpacePolicy.Managed.TYPE -> SpacePolicy.Managed.serializer()
            else -> null
        }

    override fun unknown(json: JsonObject) = SpacePolicy.Unknown(json)

    override fun encode(encoder: JsonEncoder, value: SpacePolicy) =
        when (value) {
            is SpacePolicy.Personal -> encoder.encodeSerializableValue(SpacePolicy.Personal.serializer(), value)
            is SpacePolicy.Member -> encoder.encodeSerializableValue(SpacePolicy.Member.serializer(), value)
            is SpacePolicy.Managed -> encoder.encodeSerializableValue(SpacePolicy.Managed.serializer(), value)
            is SpacePolicy.Unknown -> encoder.encodeJsonElement(value.json)
        }
}

/** `com.atproto.space.*`: the space lifecycle, credentials, and reads and writes of space repos. */
object Space {
    /** On the owner's PDS: creates a space governed by [Input.policy], anchored on the signed-in account. */
    object CreateSpace {
        @Serializable
        data class Input(
            /** The space type's NSID. */
            val spaceType: String,
            /** Omitted gets a TID. */
            val skey: String? = null,
            val policy: SpacePolicy,
        )

        @Serializable data class Output(val space: SpaceUri)

        val method = XrpcProcedure("com.atproto.space.createSpace", Input.serializer(), Output.serializer())
    }

    /** On the owner's PDS, for the owner only (a space credential isn't accepted): the space and its policy. */
    object GetSpace {
        @Serializable data class Params(val space: SpaceUri)

        @Serializable data class Output(val space: SpaceUri, val policy: SpacePolicy)

        val method = XrpcQuery("com.atproto.space.getSpace", Params.serializer(), Output.serializer())
    }

    /**
     * Replaces a space's policy wholesale; only the authority may. A policy of another `$type` migrates the space and discards the previous
     * policy's state, such as a member list.
     */
    object UpdateSpace {
        @Serializable data class Input(val space: SpaceUri, val policy: SpacePolicy)

        val method = XrpcProcedure("com.atproto.space.updateSpace", Input.serializer(), NoOutput.serializer())
    }

    /**
     * On the owner's PDS: deletes the space (the owner's own repo in it) and notifies its registered services. Needs the `space:` scope's
     * `manage=delete`. A space already deleted succeeds; one that never existed is [SpaceErrors.SPACE_NOT_FOUND], which
     * `SpaceClient.deleteSpace` takes as done.
     */
    object DeleteSpace {
        @Serializable data class Input(val space: SpaceUri)

        val method = XrpcProcedure("com.atproto.space.deleteSpace", Input.serializer(), NoOutput.serializer())
    }

    /** On the viewer's own PDS: a 60 s, single-use token naming the viewer, to exchange at the space's authority. */
    object GetDelegationToken {
        @Serializable data class Params(val space: SpaceUri)

        @Serializable data class Output(val token: String)

        val method = XrpcQuery("com.atproto.space.getDelegationToken", Params.serializer(), Output.serializer())
    }

    /**
     * On the authority's PDS, unauthenticated: a credential for reading the space, bound to [Input.credentialKey] (a fresh P-256
     * `did:key`). [Input.delegationToken] names the reader; without one the managing app is asked with no account. The output carries no
     * expiry; it's the credential JWT's `exp`. Calling it again before then (reauthorizing) works the same as the first time.
     *
     * With [Input.notify], it also registers the syncer at [Notify.endpoint] for [NotifySpaceUpdate]s, confirmed first with
     * [ConfirmRegistration] (else [SpaceErrors.REGISTRATION_NOT_CONFIRMED]). A registration lives only while the credential of its latest
     * authorization is valid, so a syncer keeps reauthorizing with the same [Notify] to keep it.
     */
    object AuthorizeSpaceSync {
        @Serializable
        data class Input(
            val space: SpaceUri,
            val credentialKey: String,
            val delegationToken: String? = null,
            val clientAttestation: String? = null,
            val notify: Notify? = null,
        )

        /**
         * Where the space host sends [NotifySpaceUpdate]: [endpoint] is a base URL (`{endpoint}/xrpc/…`), and [token] an opaque secret of
         * at most 512 characters, one per space and kept stable, which authenticates the host's calls to it.
         */
        @Serializable data class Notify(val endpoint: String, val token: String)

        /** [spaceRev] is the space's current revision, absent before its first update. */
        @Serializable data class Output(val credential: String, val spaceRev: String? = null)

        val method = XrpcProcedure("com.atproto.space.authorizeSpaceSync", Input.serializer(), Output.serializer())
    }

    object GetRecord {
        @Serializable data class Params(val space: SpaceUri, val repo: Did, val collection: String, val rkey: String)

        @Serializable data class Output(val uri: AtUri, val cid: Cid, val value: JsonObject)

        val method = XrpcQuery("com.atproto.space.getRecord", Params.serializer(), Output.serializer())
    }

    object ListRecords {
        @Serializable
        data class Params(
            val space: SpaceUri,
            val repo: Did,
            /** Omitted lists every collection. */
            val collection: String? = null,
            /** 1–1000; the PDS defaults to 50. */
            val limit: Int? = null,
            val cursor: String? = null,
        )

        @Serializable data class Record(val collection: String, val rkey: String, val cid: Cid, val value: JsonObject? = null)

        @Serializable data class Output(val records: List<Record>, val cursor: String? = null)

        val method = XrpcQuery("com.atproto.space.listRecords", Params.serializer(), Output.serializer())
    }

    /** Returns the blob's bytes (any content type), not JSON. */
    object GetBlob {
        @Serializable data class Params(val space: SpaceUri, val repo: Did, val cid: Cid)

        val method = XrpcQuery("com.atproto.space.getBlob", Params.serializer(), NoOutput.serializer())
    }

    object PutRecord {
        @Serializable
        data class Input(
            val space: SpaceUri,
            /** The writer's own DID: each member writes to their own repo in the space. */
            val repo: Did,
            val collection: String,
            val rkey: String,
            val record: JsonElement,
            /** Our lexicons are unknown to most PDSes; `false` skips schema validation there. */
            val validate: Boolean? = false,
        )

        @Serializable data class Output(val uri: AtUri, val cid: Cid)

        val method = XrpcProcedure("com.atproto.space.putRecord", Input.serializer(), Output.serializer())
    }

    object DeleteRecord {
        @Serializable data class Input(val space: SpaceUri, val repo: Did, val collection: String, val rkey: String)

        val method = XrpcProcedure("com.atproto.space.deleteRecord", Input.serializer(), JsonObject.serializer())
    }

    /**
     * A repo's operation log in a space, after a revision: how a syncer keeps up. The host may compact the log, so with no `since` it
     * returns only what it retained; a syncer backfills with [ListRecords] and follows ops from the head revision ([Output.commit]). A repo
     * never written is `RepoNotFound`.
     */
    object ListRepoOps {
        @Serializable
        data class Params(
            val space: SpaceUri,
            val repo: Did,
            /** Ops after this revision. */
            val since: String? = null,
            /** 1–1000; the PDS defaults to 100. */
            val limit: Int? = null,
            /** Takes precedence over [since]. */
            val cursor: String? = null,
            val excludeValues: Boolean? = null,
        )

        /** One write: [cid] is null for a delete, [prev] null for a create. Ops sharing a [rev] are one batch. */
        @Serializable
        data class Op(
            val rev: String,
            val collection: String,
            val rkey: String,
            val cid: Cid? = null,
            val prev: Cid? = null,
            val value: JsonObject? = null,
        )

        /**
         * `com.atproto.space.defs#commit`, unsigned: the repo host authenticates it with a MAC over its `Atproto-Space-Commit` response
         * header, which repeats [ver], [repo], [space], [rev] and [data] (the sha256 of the repo's LtHash state).
         */
        @Serializable data class Commit(val ver: Int, val repo: Did, val space: SpaceUri, val rev: String, val data: LexBytes)

        /**
         * Every page carries the repo's current [commit]. [cursor] is set whenever the page is full, so the last page (the first without
         * one) may be empty; its commit is what the ops fold to.
         */
        @Serializable data class Output(val ops: List<Op>, val commit: Commit, val cursor: String? = null)

        val method = XrpcQuery("com.atproto.space.listRepoOps", Params.serializer(), Output.serializer())
    }

    /**
     * Served by the syncer, not the PDS, with no service auth: before creating a registration, the space host asks the syncer at the
     * `notify` endpoint of an [AuthorizeSpaceSync] to echo [Input.challenge] for [Input.token], which proves the syncer issued that token
     * for [Input.space].
     */
    object ConfirmRegistration {
        @Serializable data class Input(val space: SpaceUri, val token: String, val challenge: String)

        @Serializable data class Output(val challenge: String)

        val method = XrpcProcedure("com.atproto.space.confirmRegistration", Input.serializer(), Output.serializer())
    }

    /**
     * Served by the syncer, with no service auth: the space host tells a registered syncer of an [Input.update] to the space. [Input.token]
     * is the one the syncer registered, which authenticates the call. `repoWrite`/`repoRemoved` are best effort; expiry and deletion
     * notices are retried with backoff.
     */
    object NotifySpaceUpdate {
        @Serializable data class Input(val space: SpaceUri, val token: String, val update: SpaceUpdate)

        val method = XrpcProcedure("com.atproto.space.notifySpaceUpdate", Input.serializer(), JsonObject.serializer())
    }
}

/** The `update` union of [Space.NotifySpaceUpdate]: match its `$type` in full. An open union, so a newer kind decodes as [Unknown]. */
@Serializable(with = SpaceUpdateSerializer::class)
sealed interface SpaceUpdate {
    /** `com.atproto.space.defs#repoWrite`: [repo] in the space advanced to [repoRev]; sync it from its host. */
    @Serializable
    data class RepoWrite(
        val repo: Did,
        val repoRev: String,
        /** The repo's state digest; not read. */
        val data: LexBytes? = null,
        val spaceRev: String,
        val prevSpaceRev: String? = null,
        @SerialName("\$type") val type: String = TYPE,
    ) : SpaceUpdate {
        companion object {
            const val TYPE = "com.atproto.space.defs#repoWrite"
        }
    }

    /** `com.atproto.space.defs#repoRemoved`: [repo] left the space's writer set. */
    @Serializable
    data class RepoRemoved(
        val repo: Did,
        val spaceRev: String,
        val prevSpaceRev: String? = null,
        @SerialName("\$type") val type: String = TYPE,
    ) : SpaceUpdate {
        companion object {
            const val TYPE = "com.atproto.space.defs#repoRemoved"
        }
    }

    /**
     * `com.atproto.space.notifySpaceUpdate#spaceDeleted`: the space may be gone. Only an [Space.AuthorizeSpaceSync] answered
     * [SpaceErrors.SPACE_DELETED] confirms it, so a syncer reauthorizes rather than dropping data on the notice.
     */
    @Serializable
    data class SpaceDeleted(@SerialName("\$type") val type: String = TYPE) : SpaceUpdate {
        companion object {
            const val TYPE = "com.atproto.space.notifySpaceUpdate#spaceDeleted"
        }
    }

    /**
     * `com.atproto.space.notifySpaceUpdate#registrationExpired`: the registration lapsed at [spaceRev]. Reauthorize with `notify` and catch
     * up.
     */
    @Serializable
    data class RegistrationExpired(val spaceRev: String, @SerialName("\$type") val type: String = TYPE) : SpaceUpdate {
        companion object {
            const val TYPE = "com.atproto.space.notifySpaceUpdate#registrationExpired"
        }
    }

    data class Unknown(val json: JsonObject) : SpaceUpdate
}

internal object SpaceUpdateSerializer : OpenUnionSerializer<SpaceUpdate>("SpaceUpdate") {
    override fun memberFor(type: String?) =
        when (type) {
            SpaceUpdate.RepoWrite.TYPE -> SpaceUpdate.RepoWrite.serializer()
            SpaceUpdate.RepoRemoved.TYPE -> SpaceUpdate.RepoRemoved.serializer()
            SpaceUpdate.SpaceDeleted.TYPE -> SpaceUpdate.SpaceDeleted.serializer()
            SpaceUpdate.RegistrationExpired.TYPE -> SpaceUpdate.RegistrationExpired.serializer()
            else -> null
        }

    override fun unknown(json: JsonObject) = SpaceUpdate.Unknown(json)

    override fun encode(encoder: JsonEncoder, value: SpaceUpdate) =
        when (value) {
            is SpaceUpdate.RepoWrite -> encoder.encodeSerializableValue(SpaceUpdate.RepoWrite.serializer(), value)
            is SpaceUpdate.RepoRemoved -> encoder.encodeSerializableValue(SpaceUpdate.RepoRemoved.serializer(), value)
            is SpaceUpdate.SpaceDeleted -> encoder.encodeSerializableValue(SpaceUpdate.SpaceDeleted.serializer(), value)
            is SpaceUpdate.RegistrationExpired -> encoder.encodeSerializableValue(SpaceUpdate.RegistrationExpired.serializer(), value)
            is SpaceUpdate.Unknown -> encoder.encodeJsonElement(value.json)
        }
}

/** `com.atproto.managedspace.*`: the callout to the managing app of a space governed by [SpacePolicy.Managed]. */
object ManagedSpace {
    /**
     * Served by the managing app, not the PDS: the space authority's PDS asks whether [Params.repo] may read and write the space, and uses
     * whichever answer it needs. Authenticated with a service-auth token from the authority, addressed to the managing app, whose `lxm` is
     * this method. Without a delegation token the PDS asks with no [Params.repo]: `read` there admits anyone.
     */
    object CheckAccess {
        @Serializable
        data class Params(
            val space: SpaceUri,
            /** The account asking; absent for a request with no delegation token. */
            val repo: Did? = null,
            /** The attested OAuth client (a URI), when the request presented an attestation; never sent for a check prompted by a write. */
            val clientId: String? = null,
        )

        @Serializable data class Output(val read: Boolean, val write: Boolean)

        val method = XrpcQuery("com.atproto.managedspace.checkAccess", Params.serializer(), Output.serializer())
    }
}

/** A lexicon `bytes` value, `{"$bytes": "<base64>"}` (standard alphabet, padding optional). */
@Serializable
data class LexBytes(@SerialName("\$bytes") val base64: String) {
    fun decode(): ByteArray = Base64.Default.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(base64)

    companion object {
        fun of(bytes: ByteArray) = LexBytes(Base64.Default.withPadding(Base64.PaddingOption.ABSENT).encode(bytes))
    }
}
